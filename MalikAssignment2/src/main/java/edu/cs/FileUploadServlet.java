package edu.cs;

import java.io.*;
import java.sql.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/FileUploadServlet")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 10,  // 10 MB
    maxFileSize = 1024 * 1024 * 100,       // 100 MB
    maxRequestSize = 1024 * 1024 * 100     // 100 MB
)
public class FileUploadServlet extends HttpServlet {

    private static final long serialVersionUID = 205242440643911308L;

    // Database connection parameters
    private static final String DB_URL = "jdbc:mysql://database-1.c3uyoouqibxo.us-east-2.rds.amazonaws.com:3306/db_repo";
    private static final String DB_USER = "SEuser2025";
    private static final String DB_PASSWORD = "SE2025";

    // Directory to save uploaded files (relative to web application root)
    private static final String UPLOAD_DIR = "uploads";

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        // Get application upload directory
        String applicationPath = request.getServletContext().getRealPath("");
        String uploadFilePath = applicationPath + File.separator + UPLOAD_DIR;

        // Create directory if it doesn't exist
        File fileSaveDir = new File(uploadFilePath);
        if (!fileSaveDir.exists()) {
            fileSaveDir.mkdirs();
        }

        try {
            // Loop through all uploaded parts
            for (Part part : request.getParts()) {
                String fileName = getFileName(part);

                if (fileName.isEmpty()) {
                    writeToResponse(response, "❌ Error: No file selected.");
                    return;
                }

                // Check file size limit
                if (part.getSize() > 100 * 1024 * 1024) { // 100MB
                    writeToResponse(response, "❌ Error: File size exceeds 100MB limit.");
                    return;
                }

                String fileType = part.getContentType();
                
                // Save file locally
                String filePath = uploadFilePath + File.separator + fileName;
                part.write(filePath);
                System.out.println("File saved to: " + filePath);

                // Read file content for database storage
                String fileContent = readTextFile(new File(filePath));

                // Store file in the database
                if (storeFileInDB(fileName, fileContent)) {
                    out.write("✅ File uploaded successfully and stored in database.<br>");

                    // If the file is a text file, display its content
                    if (fileType.startsWith("text")) {
                        out.write("<br><b>📄 File Content:</b><br><pre>" + fileContent + "</pre>");
                    }
                } else {
                    writeToResponse(response, "❌ Error storing file in database.");
                }
            }

            // Test MySQL connection with UTC-4 offset - Assignment 1 implementation. Returns current time
            String timeOffsetResult = testConnectionMySQL(-4);
            if (timeOffsetResult != null) {
                out.write("<br>✅ Database connection test successful.");
                out.write("<br>📅 UTC-4 time: <b>" + timeOffsetResult + "</b>");
            } else {
                out.write("<br>❌ Database connection test failed.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            writeToResponse(response, "❌ Error: " + e.getMessage());
        }
    }

    /**
     * Utility method to get file name from HTTP header content-disposition
     */
    private String getFileName(Part part) {
        String contentDisp = part.getHeader("content-disposition");
        for (String token : contentDisp.split(";")) {
            if (token.trim().startsWith("filename")) {
                return token.substring(token.indexOf("=") + 2, token.length() - 1);
            }
        }
        return "";
    }

    /**
     * Reads a text file from disk and returns its content as a String.
     */
    private String readTextFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * Stores the uploaded file in the MySQL database as a text blob.
     */
    private boolean storeFileInDB(String fileName, String fileContent) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException cnfe) {
            System.out.println("MySQL Driver not found!");
            return false;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT INTO uploaded_files (file_name, file_content) VALUES (?, ?)")) {

            pstmt.setString(1, fileName);
            pstmt.setString(2, fileContent); // Store file content as text

            int rows = pstmt.executeUpdate();
            return (rows > 0);

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("❌ Database Error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tests the MySQL connection and retrieves the system clock time with UTC-4 offset. From assignment 1
     */
    public String testConnectionMySQL(int utc_offset) {
        String connectionHost = "database-1.c3uyoouqibxo.us-east-2.rds.amazonaws.com"; // MySQL RDS endpoint
        String dbName = "db_repo";
        String dbUser = "SEuser2025";
        String dbPassword = "SE2025";

        Connection connect = null;
        PreparedStatement preparedStatement = null;
        String timeResult = null;

        try {
            // Load MySQL Driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish database connection
            connect = DriverManager.getConnection(
                    "jdbc:mysql://" + connectionHost + "/" + dbName, dbUser, dbPassword);

            // Query to get UTC-4 time
            String qry1a = "SELECT CONVERT_TZ(NOW(), @@session.time_zone, '-04:00')";
            preparedStatement = connect.prepareStatement(qry1a);

            ResultSet r1 = preparedStatement.executeQuery();
            if (r1.next()) {
                timeResult = r1.getString(1);
                System.out.println("UTC-4 time on MySQL server at " + connectionHost + " is: " + timeResult);
            }
            r1.close();
            preparedStatement.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (preparedStatement != null) preparedStatement.close();
                if (connect != null) connect.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return timeResult;
    }

    /**
     * Writes response data to the client (from instructor's example).
     */
    private void writeToResponse(HttpServletResponse resp, String results) throws IOException {
        PrintWriter writer = new PrintWriter(resp.getOutputStream());
        resp.setContentType("text/plain");

        if (results.isEmpty()) {
            writer.write("No results found.");
        } else {
            writer.write(results);
        }
        writer.close();
    }
}
