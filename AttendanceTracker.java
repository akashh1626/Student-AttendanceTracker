/*
 * STUDENT ATTENDANCE TRACKER  -  Java + JDBC + MySQL  (single file)
 * -----------------------------------------------------------------
 * 1. Install MySQL Server and download MySQL Connector/J (mysql-connector-j-x.x.x.jar)
 * 2. Change DB_PASS below to your MySQL root password
 * 3. Compile:  javac AttendanceTracker.java
 * 4. Run:
 *      Windows  : java -cp ".;mysql-connector-j-x.x.x.jar" AttendanceTracker
 *      Linux/Mac: java -cp ".:mysql-connector-j-x.x.x.jar" AttendanceTracker
 *
 * The database and tables are created automatically on first run.
 * Default login: akash / akash123
 */
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

public class AttendanceTracker {

    // ====================== DATABASE SETTINGS ======================
    static final String DB_URL  = "jdbc:mysql://localhost:3306/attendance_db?createDatabaseIfNotExist=true";
    static final String DB_USER = "root";
    static final String DB_PASS = "Aka_sh#162627";   // <-- change this

    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // Creates tables + default admin automatically
    static void initDatabase() throws SQLException {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT, " +
                    "username VARCHAR(30) UNIQUE NOT NULL, " +
                    "password_hash CHAR(64) NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS students (" +
                    "student_id INT PRIMARY KEY AUTO_INCREMENT, " +
                    "reg_no VARCHAR(20) UNIQUE NOT NULL, " +
                    "name VARCHAR(50) NOT NULL, " +
                    "department VARCHAR(30), " +
                    "year INT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS attendance (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT, " +
                    "student_id INT NOT NULL, " +
                    "att_date DATE NOT NULL, " +
                    "status ENUM('Present','Absent') NOT NULL, " +
                    "UNIQUE (student_id, att_date), " +
                    "FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE)");
            st.executeUpdate("INSERT IGNORE INTO users (username, password_hash) " +
                    "VALUES ('admin', SHA2('admin123', 256))");
        }
    }

    // ====================== LOGIN ======================
    static String sha256(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static boolean checkLogin(String username, String password) {
        String sql = "SELECT 1 FROM users WHERE username = ? AND password_hash = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, sha256(password));
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
            return false;
        }
    }

    // ====================== STUDENT OPERATIONS ======================
    static void addStudent(String regNo, String name, String dept, int year) {
        String sql = "INSERT INTO students (reg_no, name, department, year) VALUES (?, ?, ?, ?)";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, regNo);
            ps.setString(2, name);
            ps.setString(3, dept);
            ps.setInt(4, year);
            ps.executeUpdate();
            System.out.println("Student added.");
        } catch (SQLException e) {
            System.out.println("Could not add student: " + e.getMessage());
        }
    }

    static void viewStudents() {
        String sql = "SELECT student_id, reg_no, name, department, year FROM students ORDER BY student_id";
        try (Connection con = getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.printf("%-5s %-15s %-25s %-22s %-4s%n", "ID", "Reg No", "Name", "Department", "Year");
            System.out.println("-".repeat(75));
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("%-5d %-15s %-25s %-22s %-4d%n",
                        rs.getInt("student_id"), rs.getString("reg_no"), rs.getString("name"),
                        rs.getString("department"), rs.getInt("year"));
            }
            if (!any) System.out.println("No students found.");
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    static boolean studentExists(int id) {
        String sql = "SELECT 1 FROM students WHERE student_id = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            return false;
        }
    }

    static void updateStudent(int id, String name, String dept, int year) {
        String sql = "UPDATE students SET name = ?, department = ?, year = ? WHERE student_id = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, dept);
            ps.setInt(3, year);
            ps.setInt(4, id);
            System.out.println(ps.executeUpdate() > 0 ? "Updated." : "Update failed.");
        } catch (SQLException e) {
            System.out.println("Could not update: " + e.getMessage());
        }
    }

    static void deleteStudent(int id) {
        String sql = "DELETE FROM students WHERE student_id = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            System.out.println(ps.executeUpdate() > 0 ? "Deleted." : "No such student.");
        } catch (SQLException e) {
            System.out.println("Could not delete: " + e.getMessage());
        }
    }

    // ====================== ATTENDANCE OPERATIONS ======================
    static boolean markAttendance(int studentId, LocalDate date, String status) {
        String sql = "INSERT INTO attendance (student_id, att_date, status) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE status = VALUES(status)";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, status);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Could not mark attendance: " + e.getMessage());
            return false;
        }
    }

    static void markForAll(LocalDate date) {
        String sql = "SELECT student_id, reg_no, name FROM students ORDER BY student_id";
        try (Connection con = getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            boolean any = false;
            while (rs.next()) {
                any = true;
                String label = rs.getString("reg_no") + " - " + rs.getString("name");
                String status;
                while (true) {
                    System.out.print(label + " (P/A): ");
                    String in = sc.nextLine().trim().toUpperCase();
                    if (in.equals("P")) { status = "Present"; break; }
                    if (in.equals("A")) { status = "Absent";  break; }
                    System.out.println("Enter P or A.");
                }
                markAttendance(rs.getInt("student_id"), date, status);
            }
            System.out.println(any ? "Attendance saved for " + date : "No students to mark.");
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void viewByStudent(int studentId) {
        String sql = "SELECT att_date, status FROM attendance WHERE student_id = ? ORDER BY att_date";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf("%-12s %-8s%n", "Date", "Status");
                System.out.println("-".repeat(21));
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("%-12s %-8s%n", rs.getDate("att_date"), rs.getString("status"));
                }
                if (!any) System.out.println("No records.");
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void viewByDate(LocalDate date) {
        String sql = "SELECT s.reg_no, s.name, a.status FROM attendance a " +
                     "JOIN students s ON s.student_id = a.student_id WHERE a.att_date = ? ORDER BY s.student_id";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf("%-15s %-25s %-8s%n", "Reg No", "Name", "Status");
                System.out.println("-".repeat(50));
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("%-15s %-25s %-8s%n",
                            rs.getString("reg_no"), rs.getString("name"), rs.getString("status"));
                }
                if (!any) System.out.println("No records for " + date);
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void percentageReport() {
        String sql = "SELECT s.reg_no, s.name, COUNT(a.id) AS total, " +
                     "COALESCE(SUM(a.status = 'Present'), 0) AS present " +
                     "FROM students s LEFT JOIN attendance a ON s.student_id = a.student_id " +
                     "GROUP BY s.student_id, s.reg_no, s.name ORDER BY s.student_id";
        try (Connection con = getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.printf("%-15s %-25s %-7s %-8s %-8s %s%n",
                    "Reg No", "Name", "Total", "Present", "%", "Remark");
            System.out.println("-".repeat(80));
            while (rs.next()) {
                int total = rs.getInt("total");
                int present = rs.getInt("present");
                double pct = total == 0 ? 0 : present * 100.0 / total;
                String remark = total == 0 ? "No data" : (pct < 75 ? "SHORTAGE (<75%)" : "OK");
                System.out.printf("%-15s %-25s %-7d %-8d %-8.2f %s%n",
                        rs.getString("reg_no"), rs.getString("name"), total, present, pct, remark);
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ====================== INPUT HELPERS ======================
    static Scanner sc = new Scanner(System.in);

    static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try { return Integer.parseInt(sc.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.println("Enter a valid number."); }
        }
    }

    static String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            if (!s.isEmpty()) return s;
            System.out.println("This field cannot be empty.");
        }
    }

    static LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt + " (YYYY-MM-DD, blank = today): ");
            String s = sc.nextLine().trim();
            if (s.isEmpty()) return LocalDate.now();
            try { return LocalDate.parse(s); }
            catch (DateTimeParseException e) { System.out.println("Invalid date format."); }
        }
    }

    static boolean login() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            String u = readText("Username: ");
            String p = readText("Password: ");
            if (checkLogin(u, p)) { System.out.println("Login successful.\n"); return true; }
            System.out.println("Invalid credentials (" + attempt + "/3).");
        }
        System.out.println("Too many failed attempts. Exiting.");
        return false;
    }

    // ====================== MAIN ======================
    public static void main(String[] args) {
        System.out.println("===== STUDENT ATTENDANCE TRACKER =====");
        try {
            initDatabase();
        } catch (SQLException e) {
            System.out.println("Cannot connect to MySQL: " + e.getMessage());
            System.out.println("Check that MySQL is running, DB_PASS is correct, and the Connector/J jar is on the classpath.");
            return;
        }
        if (!login()) return;

        while (true) {
            System.out.println("\n1. Add Student");
            System.out.println("2. View Students");
            System.out.println("3. Update Student");
            System.out.println("4. Delete Student");
            System.out.println("5. Mark Attendance (all students)");
            System.out.println("6. Mark Attendance (single student)");
            System.out.println("7. View Attendance by Student");
            System.out.println("8. View Attendance by Date");
            System.out.println("9. Attendance Percentage Report");
            System.out.println("0. Exit");
            int choice = readInt("Choose: ");

            switch (choice) {
                case 1 -> addStudent(readText("Reg No: "), readText("Name: "),
                                     readText("Department: "), readInt("Year: "));
                case 2 -> viewStudents();
                case 3 -> {
                    int id = readInt("Student ID to update: ");
                    if (!studentExists(id)) { System.out.println("No such student."); break; }
                    updateStudent(id, readText("New name: "), readText("New department: "), readInt("New year: "));
                }
                case 4 -> deleteStudent(readInt("Student ID to delete: "));
                case 5 -> markForAll(readDate("Date"));
                case 6 -> {
                    int id = readInt("Student ID: ");
                    if (!studentExists(id)) { System.out.println("No such student."); break; }
                    LocalDate d = readDate("Date");
                    String st = readText("Status (P/A): ").toUpperCase();
                    if (st.equals("P") || st.equals("A")) {
                        if (markAttendance(id, d, st.equals("P") ? "Present" : "Absent"))
                            System.out.println("Attendance saved.");
                    } else System.out.println("Enter P or A.");
                }
                case 7 -> viewByStudent(readInt("Student ID: "));
                case 8 -> viewByDate(readDate("Date"));
                case 9 -> percentageReport();
                case 0 -> { System.out.println("Goodbye!"); return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }
}