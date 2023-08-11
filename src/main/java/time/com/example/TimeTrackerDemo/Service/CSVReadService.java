package time.com.example.TimeTrackerDemo.Service;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import time.com.example.TimeTrackerDemo.Model.EmployeeDetails;

import java.io.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.*;

import static time.com.example.TimeTrackerDemo.Constant.APIConstant.*;

@Service
@Slf4j
public class CSVReadService {

    @Value("${low.swipes}")
    private int lowSwipes;

    @Value("${high.swipes}")
    private int highSwipes;

    @Value("${in.time.actual}")
    private int inTimeActual;

    @Value("${in.time.fl}")
    private int inTimeFL;


    public ResponseEntity<InputStreamResource> readCsvFile(InputStream inputStream) {

        List<EmployeeDetails> employees = new ArrayList<>();
        HttpHeaders headers = new HttpHeaders();
        InputStreamResource resource = null;

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            for (int i = 0; i < 2; i++) {
                br.readLine(); // Skip the first 2 (empty spaces, headers)
            }
            EmployeeDetails previousEmployee = null;
            while ((line = br.readLine()) != null) {

                line = String.valueOf(replaceDoubleQuotes(line));

                String[] data = line.split(",", -1);

                // Check if data is null or has insufficient elements
                if (data == null || data.length == 1) {
                    // Handle incomplete data
                    break;
                }
                String employeeCode = data[0];
                String swipeDate = data[11];

                if (previousEmployee != null && previousEmployee.getEmployeeCode().equals(employeeCode) && previousEmployee.getSwipeDate().equals(swipeDate)) {
                    // Combine record
                    mergeEmployeeData(previousEmployee, data);
                } else {
                    // Create a new employee record
                    EmployeeDetails employeeDetails = new EmployeeDetails();
                    // Set employee details
                    setEmployeeDetails(data, employeeCode, swipeDate, employeeDetails);

                    List<String> swipeDetailsList = new ArrayList<>();

                    for (int i = 14; i < data.length - 1; i++) {
                        swipeDetailsList.add(data[i].trim());
                    }
                    calculateTimeDetails(swipeDetailsList, employeeDetails);
                    calculateTotalOfficeTime(swipeDetailsList, employeeDetails);
                    employeeDetails.setSwipeDetails(swipeDetailsList);
                    int length = data.length;
                    employeeDetails.setEmployeeCardNumber(data[length - 1]);
                    employees.add(employeeDetails);
                    previousEmployee = employeeDetails;
                }
            }
            // Generate a csv file
            String csvData = generateCsv(employees);
            ByteArrayInputStream inputStreamData = new ByteArrayInputStream(csvData.getBytes());

            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=employee-details.csv");

            resource = new InputStreamResource(inputStreamData);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    private void mergeEmployeeData(EmployeeDetails previousEmployee, String[] data) {

        List<String> swipeDetailsList = new ArrayList<>();

        for (int i = 14; i < data.length - 1; i++) {
            swipeDetailsList.add(data[i].trim());
        }
        List<String> existingSwipeDetails = new ArrayList<>(previousEmployee.getSwipeDetails());

        existingSwipeDetails.addAll(swipeDetailsList);
        // Recalculate time details and total office time
        calculateTimeDetails(existingSwipeDetails, previousEmployee);
        calculateTotalOfficeTime(existingSwipeDetails, previousEmployee);

        previousEmployee.setSwipeDetails(existingSwipeDetails);
    }

    private void setEmployeeDetails(String[] data, String employeeCode, String swipeDate, EmployeeDetails employeeDetails) {
        employeeDetails.setEmployeeCode(employeeCode);
        employeeDetails.setEmployeeName(data[1]);
        employeeDetails.setDateOfJoining(data[2]);
        employeeDetails.setEmployeeStatus(data[3]);
        employeeDetails.setBusinessUnit(data[4]);
        employeeDetails.setCity(data[5]);
        employeeDetails.setCompany(data[6]);
        employeeDetails.setDepartment(data[7]);
        employeeDetails.setDesignation(data[8]);
        employeeDetails.setLocation(data[9]);
        employeeDetails.setState(data[10]);

        employeeDetails.setSwipeDate(swipeDate);
    }

    public void calculateTotalOfficeTime(List<String> swipeDetails, EmployeeDetails employeeDetails) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        if (isValidSwipeDetails(swipeDetails)) {
            try {
                if (swipeDetails.size() == 1)
                    handleSingleSwipe(swipeDetails, employeeDetails);
                if (swipeDetails.size() >= 2)
                    handleMultipleSwipes(swipeDetails, employeeDetails, sdf);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        } else {
            log.info("Swipe details empty!");
            handleEmptySwipes(employeeDetails);
        }
    }

    private static void handleEmptySwipes(EmployeeDetails employeeDetails) {

        employeeDetails.setTotalTime("0");
        // Set in Time FL in minute
        employeeDetails.setInTimeFL("0");
        employeeDetails.setFlagForFirstSwipeAfter11AM(false);
        employeeDetails.setFlagForLastSwipeAfter10PM(false);
        employeeDetails.setInTimeActualVsInTimeFLDiff("0");
        employeeDetails.setSwipeCount(0);
        employeeDetails.setLess540FL(true);
        employeeDetails.setHighSwipes(false);
        employeeDetails.setLowSwipes(true);
    }

    private void handleMultipleSwipes(List<String> swipeDetails, EmployeeDetails employeeDetails, SimpleDateFormat sdf) throws ParseException {
        employeeDetails.setFirstSwipe(swipeDetails.get(0));
        employeeDetails.setLastSwipe(swipeDetails.get(swipeDetails.size() - 1));

        Date firstPunchIn = sdf.parse(swipeDetails.get(0));
        Date lastPunchIn = sdf.parse(swipeDetails.get(swipeDetails.size() - 1));

        long totalOfficeTimeMillis = lastPunchIn.getTime() - firstPunchIn.getTime();

        employeeDetails.setTotalTime(formatTime(totalOfficeTimeMillis));

        // Check First swipe after 11 AM
        LocalTime elevenAM = LocalTime.of(11, 0, 0);
        // Check last swipe is after the 10:00PM : 22:00:00
        LocalTime tenPM = LocalTime.of(22, 0, 0);

        if (Objects.nonNull(swipeDetails) && !swipeDetails.isEmpty()) {

            String firstSwipe = swipeDetails.get(0);
            if (!firstSwipe.isEmpty()) {
                // Parse the first swipe
                LocalTime firstSwipeTargetTime = LocalTime.parse(firstSwipe);
                // Compare the target time with 11 AM
                if (firstSwipeTargetTime.isAfter(elevenAM))
                    employeeDetails.setFlagForFirstSwipeAfter11AM(true);
                else
                    employeeDetails.setFlagForFirstSwipeAfter11AM(false);
            }

            String lastSwipe = swipeDetails.get(swipeDetails.size() - 1);
            if (!lastSwipe.isEmpty()) {
                // Parse the last swipe
                LocalTime lastSwipeTargetTime = LocalTime.parse(lastSwipe);
                // Compare the target time with 10 PM
                if (lastSwipeTargetTime.isAfter(tenPM))
                    employeeDetails.setFlagForLastSwipeAfter10PM(true);
                else
                    employeeDetails.setFlagForLastSwipeAfter10PM(false);
            } else
                employeeDetails.setFlagForLastSwipeAfter10PM(false);
        } else {
            employeeDetails.setFlagForFirstSwipeAfter11AM(false);
            employeeDetails.setFlagForLastSwipeAfter10PM(false);
        }

        // convert into minutes
        long inTimeFLMinutes = totalOfficeTimeMillis / TIME;
        long absoluteInTimeFLMinutes = Math.abs(inTimeFLMinutes);  // handle negative values
        // Set In Time FL in Minutes
        employeeDetails.setInTimeFL(String.valueOf(absoluteInTimeFLMinutes));

        // Set difference between In Time Actual and In Time FL
        long inTimeActualVsInTimeFLDiffMinutes = employeeDetails.getTotalInTime() - inTimeFLMinutes;
        long absoluteDiffMinutes = Math.abs(inTimeActualVsInTimeFLDiffMinutes);

        employeeDetails.setInTimeActualVsInTimeFLDiff(String.valueOf(absoluteDiffMinutes));

        if (inTimeFLMinutes < 540)
            employeeDetails.setLess540FL(true);
        else
            employeeDetails.setLess540FL(false);

        employeeDetails.setSwipeCount(swipeDetails.size());

        if (swipeDetails.size() < 6)
            employeeDetails.setLowSwipes(true);
        else
            employeeDetails.setLowSwipes(false);

        if (swipeDetails.size() > 10)
            employeeDetails.setHighSwipes(true);
        else
            employeeDetails.setHighSwipes(false);

    }

    private static void handleSingleSwipe(List<String> swipeDetails, EmployeeDetails employeeDetails) {

        employeeDetails.setFirstSwipe(swipeDetails.get(0));

        LocalTime elevenAM = LocalTime.of(11, 0, 0);
        // Parse the first swipe
        if (Objects.nonNull(swipeDetails) && !swipeDetails.isEmpty()) {
            String firstSwipe = swipeDetails.get(0);
            if (!firstSwipe.isEmpty()) {
                LocalTime targetTime = LocalTime.parse(firstSwipe);
                // Compare the target time with 11 AM
                if (targetTime.isAfter(elevenAM))
                    employeeDetails.setFlagForFirstSwipeAfter11AM(true);
                else
                    employeeDetails.setFlagForFirstSwipeAfter11AM(false);
            } else {
                employeeDetails.setFlagForFirstSwipeAfter11AM(false);
                employeeDetails.setFlagForLastSwipeAfter10PM(false);
            }
        } else {
            employeeDetails.setFlagForFirstSwipeAfter11AM(false);
            employeeDetails.setFlagForLastSwipeAfter10PM(false);
        }
        employeeDetails.setLastSwipe("00:00:00");
        employeeDetails.setSwipeCount(swipeDetails.size());
        employeeDetails.setTotalTime("0");
        // Set In time FL in minutes
        employeeDetails.setInTimeFL("0");
        employeeDetails.setInTimeActualVsInTimeFLDiff("0");
        employeeDetails.setLess540FL(true);
        employeeDetails.setLowSwipes(true);
        employeeDetails.setHighSwipes(false);
    }


    public static boolean isValidSwipeDetails(List<String> swipeDetails) {
        return !(swipeDetails.isEmpty() ||
                swipeDetails.contains(EMPTY_SWIPE) ||
                swipeDetails.contains(SWIPE_DETAILS));
    }

    private StringBuilder replaceDoubleQuotes(String line) {
        StringBuilder output = new StringBuilder();
        boolean insideQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                insideQuotes = !insideQuotes;
            }
            if (insideQuotes && c == ',') {
                output.append('#'); // Replace comma with hash
            } else {
                output.append(c);
            }
        }
        return output;
    }

    private void calculateTimeDetails(List<String> swipeDetailsList, EmployeeDetails employeeDetails) {
        if (!(swipeDetailsList.isEmpty() || swipeDetailsList.contains(SWIPE_DETAILS) || swipeDetailsList.equals(Collections.singletonList(EMPTY_SWIPE)))) {
            if (swipeDetailsList.size() % 2 == 0) {
                employeeDetails.setOddSwipes(false);
                // For even entry
                calculateTime(swipeDetailsList, employeeDetails);
            } else {
                // For odd entry
                List<String> swipeDetailsList1 = new ArrayList<>(swipeDetailsList);
                swipeDetailsList1.remove(swipeDetailsList1.size() - 1);
                employeeDetails.setOddSwipes(true);
                calculateTime(swipeDetailsList1, employeeDetails);
            }
        }
    }

    private void calculateTime(List<String> swipeDetails, EmployeeDetails employeeDetails) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        long totalInTime = 0;
        long totalOutTime = 0;

        try {
            for (int i = 0; i < swipeDetails.size(); i += 2) {
                Date punchIn = sdf.parse(swipeDetails.get(i));
                Date punchOut = sdf.parse(swipeDetails.get(i + 1));

                if (i + 2 < swipeDetails.size()) {
                    Date nextPunchIn = sdf.parse(swipeDetails.get(i + 2));
                    totalOutTime += nextPunchIn.getTime() - punchOut.getTime();
                }
                totalInTime += punchOut.getTime() - punchIn.getTime();
            }
            employeeDetails.setTotalOutTime(formatTime(totalOutTime));
            // convert into minutes
            long totalInTimeMinutes = totalInTime / TIME;

            long absoluteTotalInTimeMinutes = Math.abs(totalInTimeMinutes);  // handle negative values
            // Set In TimeActual In Minutes
            employeeDetails.setTotalInTime(absoluteTotalInTimeMinutes);

            if (totalInTimeMinutes < 540) {
                employeeDetails.setLess540Actual(true);
            } else {
                employeeDetails.setLess540Actual(false);
            }

        } catch (ParseException e) {
            log.error("Error while calculating time" + e);
            e.printStackTrace();
        }
    }

    public static String formatTime(long milliseconds) {
        long hours = milliseconds / (60 * 60 * 1000);
        long minutes = (milliseconds % (60 * 60 * 1000)) / (60 * 1000);
        long seconds = (milliseconds % (60 * 1000)) / 1000;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public String generateCsv(List<EmployeeDetails> data) throws IOException {
        StringWriter stringWriter = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(stringWriter);

        // Write CSV header column
        String[] header = {"EmployeeCode", "Employee Name", "Date of Joining", "EmployeeStatus", "BusinessUnit", "City", "Company", "Department", "Designation", "Location", "State", "Employee Card Number", "Date", "Swipes count", "In Time-Actual", "Less 540-Actual",
                "First swipe", "Last Swipe", "In Time-FL", "Less 540-FL", "Low Swipes", "High Swipes", "Odd swipes", "First Swipe After 11:00AM", "Last Swipe After 10:00PM", "InTimeActual Vs InTimeFL Diff", "Swipe Details"};

        csvWriter.writeNext(header);

        // Write data in rows
        for (EmployeeDetails item : data) {

            List<String> swipeDetails = item.getSwipeDetails();
            StringBuilder swipeDetailsStringBuilder = new StringBuilder();

            if (Objects.nonNull(swipeDetails) && !swipeDetails.isEmpty()) {
                for (String swipe : swipeDetails) {
                    swipeDetailsStringBuilder.append(swipe).append("  ");
                }
            }

            String[] row = {item.getEmployeeCode(), item.getEmployeeName(), item.getDateOfJoining(), item.getEmployeeStatus(), item.getBusinessUnit(), item.getCity(), item.getCompany(), item.getDepartment(), item.getDesignation(), item.getLocation(), item.getState(), item.getEmployeeCardNumber(), item.getSwipeDate(), String.valueOf(item.getSwipeCount()), String.valueOf(item.getTotalInTime()), String.valueOf(item.isLess540Actual()), item.getFirstSwipe(),
                    item.getLastSwipe(), item.getInTimeFL(), String.valueOf(item.isLess540FL()), String.valueOf(item.isLowSwipes()), String.valueOf(item.isHighSwipes()), String.valueOf(item.isOddSwipes()),
                    String.valueOf(item.isFlagForFirstSwipeAfter11AM()), String.valueOf(item.isFlagForLastSwipeAfter10PM()), item.getInTimeActualVsInTimeFLDiff(), swipeDetailsStringBuilder.toString()};  // item.getSwipeDetails().toString()

            csvWriter.writeNext(row);
        }
        csvWriter.close();
        return stringWriter.toString();
    }


    public XSSFWorkbook convertExcelToCsv(File file) {

        try {
            // Read Excel file and convert to CSV format
            FileInputStream inputStream = new FileInputStream(file);
            Workbook workbook = new XSSFWorkbook(inputStream);

            Sheet sheet = workbook.getSheetAt(0);

            File csvOutputFile = new File("output.csv");
            FileWriter csvWriter = new FileWriter(csvOutputFile);

            for (Row row : sheet) {
                Iterator<Cell> cellIterator = row.cellIterator();
                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    csvWriter.append(cell.toString());
                    if (cellIterator.hasNext()) {
                        csvWriter.append(",");
                    }
                }
                csvWriter.append("\n");
            }
            csvWriter.flush();
            csvWriter.close();
            workbook.close();

            // Calling readCsvFile()
            ResponseEntity<InputStreamResource> csvResponse = readCsvFile(new FileInputStream(csvOutputFile));

            // Convert CSV response to XLSX response
            XSSFWorkbook xlsxWorkbook = convertCsvToXlsx(csvResponse.getBody().getInputStream());

            return xlsxWorkbook;

        } catch (IOException e) {
            log.error("Error while converting excel to csv: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private XSSFWorkbook convertCsvToXlsx(InputStream csvInputStream) throws IOException {

        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("CSVData");

        BufferedReader csvReader = new BufferedReader(new InputStreamReader(csvInputStream));
        String row;
        int rowNum = 0;
        while ((row = csvReader.readLine()) != null) {

            String[] data = row.split(",");

            Row excelRow = sheet.createRow(rowNum++);

            int cellNum = 0;
            for (String cellData : data) {
                // Remove double quotes
                if (cellData.startsWith("\"") && cellData.endsWith("\"")) {
                    cellData = cellData.substring(1, cellData.length() - 1);
                }
                excelRow.createCell(cellNum++).setCellValue(cellData);
            }
        }
        csvReader.close();
        return workbook;
    }

}


