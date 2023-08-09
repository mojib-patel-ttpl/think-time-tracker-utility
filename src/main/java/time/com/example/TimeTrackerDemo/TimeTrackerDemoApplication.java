package time.com.example.TimeTrackerDemo;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import time.com.example.TimeTrackerDemo.Service.CSVReadService;
import time.com.example.TimeTrackerDemo.Service.ExcelUtility;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

@SpringBootApplication
public class TimeTrackerDemoApplication {

	public static void main(String[] args) {

		if (args.length == 0) {
			System.out.println("Usage: java -jar YourJarName.jar <file-path>");
			return;
		}

		String filePath = args[0];
		File inputFile = new File(filePath);

		if (!inputFile.exists()) {
			System.out.println("File does not exist: " + filePath);
			return;
		}else{
			ExcelUtility excelUtility = new ExcelUtility();
			CSVReadService csvReadService = new CSVReadService();
			XSSFWorkbook workbook = csvReadService.convertExcelToCsv(inputFile);

			// Save the workbook to the specified output file
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd_HH:mm:ss");
			String currentDate = dateFormat.format(new Date());
			String outputFileName = "Report_" + currentDate + ".xlsx";
			String folderName = "employee_xlsx_reports";
			excelUtility.saveWorkbook(workbook, folderName, outputFileName);

		}
		SpringApplication.run(TimeTrackerDemoApplication.class, args);
	}

}
