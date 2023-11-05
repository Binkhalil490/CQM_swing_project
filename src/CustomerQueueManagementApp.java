import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.ServiceUI;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.PageRanges;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CustomerQueueManagementApp extends JFrame {

    private LinkedList<Token> customerQueue;
    private JLabel servingLabel;
    private DefaultListModel<Token> queueListModel;
    private JList<Token> queueList;

    // Database connection details
    private static final String DB_URL = "jdbc:oracle:thin:@//localhost:1521/pdborcl";
    private static final String DB_USERNAME = "hr";
    private static final String DB_PASSWORD = "hr";
    private Connection connection;

    public CustomerQueueManagementApp() {
        // Initialize the customer queue
        customerQueue = new LinkedList<>();

        // Set up the main window
        setTitle("Customer Queue Management");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create and add the serving label
        servingLabel = new JLabel("No customer serving at the moment");
        servingLabel.setHorizontalAlignment(JLabel.CENTER);
        add(servingLabel, BorderLayout.NORTH);

        // Create and add the queue list
        queueListModel = new DefaultListModel<>();
        queueList = new JList<>(queueListModel);
        add(new JScrollPane(queueList), BorderLayout.CENTER);

        // Create and add the generate token button
        JButton generateTokenButton = new JButton("Generate Token");
        generateTokenButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                generateToken();
            }
        });
        add(generateTokenButton, BorderLayout.SOUTH);

        // Create and add the print button
        JButton printButton = new JButton("Print Token");
        printButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                printToken();
            }
        });
        add(printButton, BorderLayout.EAST);

        // Create and add the next call button
        JButton nextCallButton = new JButton("Next Call");
        nextCallButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                serveNextCustomer();
            }
        });
        add(nextCallButton, BorderLayout.WEST);

        // Connect to the Oracle database
        connectToDatabase();
    }

    private void connectToDatabase() {
        try {
            // Register the Oracle JDBC driver
            Class.forName("oracle.jdbc.driver.OracleDriver");
            // Create the database connection
            connection = DriverManager.getConnection("jdbc:oracle:thin:@//localhost:1521/pdborcl", "hr", "hr");
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void generateToken() {
        // Create a panel to display the input fields
        JPanel panel = new JPanel(new GridLayout(0, 2));

        // Add text field for customer name
        JTextField nameField = new JTextField();
        panel.add(new JLabel("Customer Name:"));
        panel.add(nameField);

        // Add text field for token description
        JTextField descriptionField = new JTextField();
        panel.add(new JLabel("Token Description:"));
        panel.add(descriptionField);

        // Add problem type combo box
        JComboBox<String> problemTypeComboBox = new JComboBox<>(new String[]{"Hardware", "Software", "Other"});
        panel.add(new JLabel("Problem Type:"));
        panel.add(problemTypeComboBox);

        // Add text field for phone number
        JTextField phoneNumberField = new JTextField();
        panel.add(new JLabel("Phone Number (11 digits):"));
        panel.add(phoneNumberField);

        // Show the panel and get the user's input
        int result = JOptionPane.showConfirmDialog(null, panel, "Generate Token",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {

            String name = nameField.getText();
            String description = descriptionField.getText();
            String problemType = (String) problemTypeComboBox.getSelectedItem();
            String phoneNumber = phoneNumberField.getText();

            // Generate a new token with a unique serial number
            Token token = new Token(name, description, problemType, phoneNumber, new Date());

            // Add the token to the customer queue
            customerQueue.add(token);
            queueListModel.addElement(token);

            // Insert the token into the database
            insertTokenIntoDatabase(token);

            // Update serving label
            updateServingLabel();
        }
    }

    private void insertTokenIntoDatabase(Token token) {
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO token (SERIAL_NUMBER, NAME, DESCRIPTION, PROBLEM_TYPE, PHONE_NUMBER, SL_DATE) " +
                            "VALUES (?, ?, ?, ?, ?, ?)");

            statement.setInt(1, token.getSerialNumber());
            statement.setString(2, token.getName());
            statement.setString(3, token.getDescription());
            statement.setString(4, token.getProblemType());
            statement.setString(5, token.getPhoneNumber());
            statement.setDate(6, new java.sql.Date(new Date().getTime()));

            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void printToken() {
        Token selectedToken = queueList.getSelectedValue();
        if (selectedToken != null) {
            String documentContent = "Token Number: " + selectedToken.getSerialNumber() + "\n" +
                    "Customer Name: " + selectedToken.getName() + "\n" +
                    "Description: " + selectedToken.getDescription() + "\n" +
                    "Problem Type: " + selectedToken.getProblemType() + "\n" +
                    "Phone Number: " + selectedToken.getPhoneNumber() + "\n" +
                    "Date: " + getCurrentDate();

            Printable printable = new Printable() {
//                @Override
//                public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
//                    return 0;
//                }

                @Override
                public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
                    if (pageIndex > 0) {
                        return NO_SUCH_PAGE;
                    }

                    Graphics2D g2d = (Graphics2D) graphics;
                    g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

                    Font font = new Font("Arial", Font.PLAIN, 12);
                    g2d.setFont(font);

                    int lineHeight = g2d.getFontMetrics().getHeight();
                    int y = lineHeight;

                    String[] lines = documentContent.split("\n");
                    for (String line : lines) {
                        g2d.drawString(line, 0, y);
                        y += lineHeight;
                    }

                    return PAGE_EXISTS;
                }
            };

            try {
                PrintService defaultPrintService = PrintServiceLookup.lookupDefaultPrintService();
                if (defaultPrintService != null) {
                    // Prepare print job attributes
                    PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
                    attributes.add(new Copies(1));
                    attributes.add(new PageRanges(1));

                    // Show print dialog
                    PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, attributes);
                    PrintService printService = ServiceUI.printDialog(null, 200, 200, printServices,
                            defaultPrintService, null, attributes);

                    if (printService != null) {
                        // Print the document using the selected print service
                        PrinterJob job = PrinterJob.getPrinterJob();
                        job.setPrintService(printService);
                        job.setPrintable(printable);

                        if (job.printDialog()) {
                            job.print();
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "No default printer available.");
                }
            } catch (PrinterException e) {
                JOptionPane.showMessageDialog(null, "Error occurred while printing: " + e.getMessage());
            }
        } else {
            JOptionPane.showMessageDialog(null, "Please select a token from the queue to print.");
        }
    }


    public void generateSalesReport() {
        try {
            // Prepare and execute the SQL query to fetch data for the report
            Statement statement = connection.createStatement();
            String query = "SELECT * FROM token";
            ResultSet resultSet = statement.executeQuery(query);

            // Generate the sales report
            StringBuilder report = new StringBuilder();
            while (resultSet.next()) {
                int serialNumber = resultSet.getInt("SERIAL_NUMBER");
                String name = resultSet.getString("NAME");
                String description = resultSet.getString("DESCRIPTION");
                String problemType = resultSet.getString("PROBLEM_TYPE");
                String phoneNumber = resultSet.getString("PHONE_NUMBER");
                Date date = resultSet.getDate("SL_DATE");

                report.append("Serial Number: ").append(serialNumber).append("\n");
                report.append("Name: ").append(name).append("\n");
                report.append("Description: ").append(description).append("\n");
                report.append("Problem Type: ").append(problemType).append("\n");
                report.append("Phone Number: ").append(phoneNumber).append("\n\n");
                report.append("Date: ").append(date).append("\n");
            }

            // Write the sales report to a text file
            try (FileWriter writer = new FileWriter("sales_report.txt")) {
                writer.write(report.toString());
            }

            // Close the result set and statement
            resultSet.close();
            statement.close();

            System.out.println("Sales report generated successfully.");

            // Add a logging statement to check the current working directory
            System.out.println("Current working directory: " + System.getProperty("user.dir"));
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }




    private void serveNextCustomer() {
        if (!customerQueue.isEmpty()) {
            Token nextCustomer = customerQueue.poll();
            queueListModel.removeElement(nextCustomer);

            // Update the problem status in the database
            updateProblemStatusInDatabase(nextCustomer.getSerialNumber(), true);

            // Update serving label
            updateServingLabel();
        }
        generateSalesReport();
    }

    private void updateProblemStatusInDatabase(int serialNumber, boolean solved) {
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "UPDATE token SET solved = ? WHERE serial_number = ?");

            statement.setInt(1, solved ? 1 : 0);
            statement.setInt(2, serialNumber);

            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateServingLabel() {
        if (!customerQueue.isEmpty()) {
            Token nextCustomer = customerQueue.peek();
            servingLabel.setText("Serving: Token " + nextCustomer.getSerialNumber() + " - " + nextCustomer.getName());
        } else {
            servingLabel.setText("No customer serving at the moment");
        }
    }

    private static class Token {
        private static int nextSerialNumber = 1;

        private int serialNumber;
        private String name;
        private String description;
        private String problemType;
        private String phoneNumber;
        private Date date;

        public Token(String name, String description, String problemType, String phoneNumber, Date date) {
            this.serialNumber = nextSerialNumber++;
            this.name = name;
            this.description = description;
            this.problemType = problemType;
            this.phoneNumber = phoneNumber;
            this.date = date;
        }

        public int getSerialNumber() {
            return serialNumber;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getProblemType() {
            return problemType;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public Date getDate() {
            return date;
        }

        @Override
        public String toString() {
            return "Token " + serialNumber + ": " + name + " - " + problemType + " (Date : " + date + ")";
        }
    }

    private String getCurrentDate() {
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return currentDate.format(dateFormatter);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                LoginApp loginApp = new LoginApp();
                loginApp.setVisible(true);
                loginApp.setSize(800, 500);
                new LoginApp();
            }
        });
    }
}

class LoginApp extends JFrame {

    private JTextField usernameField;
    private JButton loginButton;

    public LoginApp() {
        setTitle("Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        setLayout(new BorderLayout());
        setSize(400,500);
//
        // Create and add the login panel
        JPanel loginPanel = new JPanel(new GridLayout(5,5));
        add(loginPanel, BorderLayout.CENTER);
//
//
        // Add username label and field
        JLabel usernameLabel = new JLabel("Username:");
        usernameField = new JTextField();
        loginPanel.add(usernameLabel);
        loginPanel.add(usernameField);
//
        // Add login button
        loginButton = new JButton("Login");
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                if (username.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Please enter a username.");
                } else {
                    // Start the customer queue management app
                    CustomerQueueManagementApp app = new CustomerQueueManagementApp();
                    app.setSize(1000, 800);
                    app.setVisible(true);
                    dispose(); // Close the login window
                }
            }
        });
        add(loginButton, BorderLayout.SOUTH);
//
        pack();
        setLocationRelativeTo(null); // Center the login window on the screen
    }
    
}
