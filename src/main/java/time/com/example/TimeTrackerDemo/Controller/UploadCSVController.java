package time.com.example.TimeTrackerDemo.Controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import time.com.example.TimeTrackerDemo.CustomException.ErrorResponse;
import time.com.example.TimeTrackerDemo.Service.CSVReadService;

import static time.com.example.TimeTrackerDemo.Constant.APIConstant.FILE;
import static time.com.example.TimeTrackerDemo.Constant.APIConstant.FILE_REQUIRED;


@RestController
public class UploadCSVController {
    @Autowired
    private CSVReadService csvReadService;

    @PostMapping("/calculate-employee-details")
    public ResponseEntity<?> calculateEmployeeDetails(@RequestParam(FILE) MultipartFile file) {

        if (file.isEmpty()) {
            ErrorResponse errorResponse = new ErrorResponse(FILE_REQUIRED);
            return ResponseEntity.badRequest().body(errorResponse);
        }
        String originalFilename = file.getOriginalFilename();

        if (StringUtils.endsWithIgnoreCase(originalFilename, ".xlsx")) {
            return csvReadService.convertExcelToCsv(file);
        } else {
            ErrorResponse errorResponse = new ErrorResponse("Unsupported file format!");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

}
