package time.com.example.TimeTrackerDemo.Service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ExcelUtility {

    public void saveWorkbook(XSSFWorkbook workbook, String folderName, String fileName) {
        try {
            File folder = new File(folderName);
            if (!folder.exists()) {
                folder.mkdir(); // Create the folder if it doesn't exist
            }

            File outputFile = new File(folder, fileName);

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Error saving Excel file: " + e.getMessage());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Error creating directory: " + ex.getMessage());
        }
    }


}
