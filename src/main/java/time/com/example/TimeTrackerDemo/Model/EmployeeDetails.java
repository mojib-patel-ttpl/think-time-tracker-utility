package time.com.example.TimeTrackerDemo.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeDetails {

    private String employeeCode;

    private String employeeName;

    private String dateOfJoining;

    private String employeeStatus;

    private String businessUnit;

    private String city;

    private String company;

    private String department;

    private String designation;

    private String location;

    private String state;

    private String swipeDate;

    private List<String> swipeDetails;

    private String employeeCardNumber;

    private int swipeCount;

    private boolean less540Actual = false;

    private String firstSwipe;

    private String lastSwipe;

    private String inTimeFL;

    private boolean less540FL = false;

    private boolean lowSwipes = false;

    private boolean highSwipes = false;

    private boolean oddSwipes;

    private long totalInTime;

    private String totalOutTime;

    private String totalTime;

    private boolean flagForFirstSwipeAfter11AM = false;

    private boolean flagForLastSwipeAfter10PM = false;

    private String inTimeActualVsInTimeFLDiff;

}
