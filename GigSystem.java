import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.sql.Timestamp;
import java.util.Vector;

import javax.naming.spi.DirStateFactory.Result;

public class GigSystem {

    public static void main(String[] args) {

        Connection conn = getSocketConnection();

        boolean repeatMenu = true;

        while(repeatMenu){
            System.out.println("_________________________");
            System.out.println("________GigSystem________");
            System.out.println("_________________________");
            System.out.println("1: Get gig line-Up");
            System.out.println("2: Organise a gig");
            System.out.println("3: Book a ticket");
            System.out.println("4: Cancel an act");
            System.out.println("5: Get tickets needed to be sold for each gig");
            System.out.println("6: Get number of tickets sold for every headline act");
            System.out.println("7: Get regular customers for each gig");
            System.out.println("8: Get economically feasible gigs");

            System.out.println("q: Quit");

            String menuChoice = readEntry("Please choose an option: ");

            if(menuChoice.length() == 0){
                //Nothing was typed (user just pressed enter) so start the loop again
                continue;
            }
            char option = menuChoice.charAt(0);

            /**
             * Reads input from user
             */
            switch(option){
                case '1':
                    String gigID_choice = readEntry("Please enter a gigID: "); /* IMPROVEMENT: Can be modified to just insert gigname and finds the corresponding gigID */
                    Integer value = 0;
                    try{
                        value = Integer.valueOf(gigID_choice);
                    } catch (NumberFormatException e){
                        System.out.println("Invalid integer input");
                        break;
                    }
                    task1(conn, value);
                    break;

                case '2':
                    String venue = readEntry("Please enter a venue: ");
                    String gigTitle = readEntry("Enter the title of a gig: ");

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String startDate = readEntry("Enter the start date of the gig (yyyy-MM-dd HH:mm:ss): ");
                    LocalDateTime start = LocalDateTime.now();
                    try{
                        start = LocalDateTime.parse(startDate, formatter);
                    }catch (DateTimeParseException e){
                        System.out.println("Invalid LocalDateTime input ");
                        break;
                    }
                    String adultTicketPrice = readEntry("Enter the ticket price (adult) of the gig: ");
                    int ticketPrice = 0;
                    try{
                        ticketPrice = Integer.valueOf(adultTicketPrice);
                    }catch (NumberFormatException e){
                        System.out.println("Invalid Integer input ");
                        break;
                    }

                    ArrayList<ActPerformanceDetails> actDetails = new ArrayList<>();
                    String actname = "";
                    while(true){
                        actname = readEntry("Enter the act name you want to insert into the gig or enter q to stop: ");
                        if (actname.equals("q")){
                            break;
                        }
                        try{
                            String query = "SELECT actID FROM act WHERE actname = ?";
                            PreparedStatement queryPreparedStatement = conn.prepareStatement(query);
                            queryPreparedStatement.setString(1, actname);
                            ResultSet result = queryPreparedStatement.executeQuery();

                            int actID = 0;
                            if(result.next()){
                                actID = result.getInt("actID");
                            }
                            String actGigFeeStr = readEntry("Enter the actgigfee: ");
                            int actGigFee = Integer.valueOf(actGigFeeStr);

                            String time = readEntry("Enter the ontime of the act (yyyy-mm-dd HH:mm:ss): ");
                            LocalDateTime ontime = LocalDateTime.now();
                            // Convert from String into LocalDateTime using a formatter
                            try{
                                ontime = LocalDateTime.parse(time, formatter);
                            } catch(DateTimeParseException e){
                                System.out.println("Invalid LocalDateTime input ");
                                break;
                            }

                            String durationString = readEntry("Enter the duration: ");
                            int duration = Integer.valueOf(durationString);
                            ActPerformanceDetails temp = new ActPerformanceDetails(actID, actGigFee, ontime, duration);
                            actDetails.add(temp);

                        } catch(SQLException e){
                            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    ActPerformanceDetails[] actDetailsArray = actDetails.toArray(new ActPerformanceDetails[0]);
                    task2(conn, venue, gigTitle, start, ticketPrice, actDetailsArray);
                    break;

                case '3':
                    String gigStr = readEntry("Enter the gigID: ");
                    int gigID = 0;
                    try{
                        gigID = Integer.valueOf(gigStr);
                    } catch(NumberFormatException e){
                        System.out.println("Invalid Integer input ");
                        break;
                    }
                    String name = readEntry("Enter the name of the customer: ");
                    String email = readEntry("Enter the email of the address: ");
                    String type = readEntry("Enter the ticket type: ");
                    task3(conn, gigID, name, email, type);
                    break;
                    
                case '4':
                    String gigEntry = readEntry("Enter the gigID you would like to modify: ");
                    int gigID1 = 0;
                    try{
                        gigID1 = Integer.valueOf(gigEntry);
                    } catch(NumberFormatException e){
                        System.out.println("Invalid Integer input ");
                        break;
                    }
                    String actnameString = readEntry("Enter the name of the act you want to cancel: ");
                    task4(conn, gigID1, actnameString);
                    break;

                case '5':
                    task5(conn);
                    break;

                case '6':
                    task6(conn);
                    break;

                case '7':
                    task7(conn);
                    break;

                case '8':
                    task8(conn);
                    break;

                case 'q':
                    repeatMenu = false;
                    break;

                default: 
                    System.out.println("Invalid option");
            }
        }
    }

    /*
     * You should not change the names, input parameters or return types of any of the predefined methods in GigSystem.java
     * You may add extra methods if you wish (and you may overload the existing methods - as long as the original version is implemented)
     */

    /**
     * Finds the line up of all the acts performing at a certain gig
     * 
     * @param conn An open database connection
     * @param gigID The gigID to list out all the acts performing at the gig
     * @return line up of all the acts performing at the gig
     */ 
    public static String[][] task1(Connection conn, int gigID){
        String[][] result;
        String selectQuery = "SELECT a.actname as \"Act Name\", TO_CHAR(ag.ontime, 'HH24:MI') as \"On Time\", TO_CHAR(ag.ontime + INTERVAL '1 minute' *ag.duration, 'HH24:MI') as \"Off Time\" FROM act a, act_gig ag WHERE a.actID = ag.actID AND gigID = ? ORDER BY ag.ontime ASC";
        try {
            PreparedStatement preparedStatement = conn.prepareStatement(selectQuery);
            preparedStatement.setInt(1, gigID);
            ResultSet lineUp = preparedStatement.executeQuery();
            
            result = convertResultToStrings(lineUp);
            printTable(result);
            preparedStatement.close();
            lineUp.close();
            return result;
        } catch (SQLException e){
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Sets up a new gig at a given venue
     * 
     * @param conn An open database connection
     * @param venue Venue of the gig
     * @param gigTitle Title of the gig
     * @param gigStart Start date of the gig
     * @param adultTicketPrice Ticket price 
     * @param actDetails Contains the actID, fee, the time the act starts and the duration of the act
     */
    public static void task2(Connection conn, String venue, String gigTitle, LocalDateTime gigStart, int adultTicketPrice, ActPerformanceDetails[] actDetails){
        // Find the venueID for the given venuename
        String selectQuery = "SELECT venueID FROM venue WHERE venuename = ?";
        try{
            conn.setAutoCommit(false);
            PreparedStatement venueIDStatement = conn.prepareStatement(selectQuery);
            venueIDStatement.setString(1, venue);
            ResultSet venueIDset = venueIDStatement.executeQuery();
            int venueID = 0;
            if (venueIDset.next()){
                venueID = venueIDset.getInt("venueID");
            }

            // Insert into the gig table
            PreparedStatement gigInsertStatement = conn.prepareStatement("INSERT INTO gig (venueID, gigtitle, gigdatetime, gigstatus) VALUES (?,?,?,?) RETURNING gigID");
            gigInsertStatement.setInt(1, venueID);
            gigInsertStatement.setString(2, gigTitle);
            gigInsertStatement.setTimestamp(3, Timestamp.valueOf(gigStart));
            gigInsertStatement.setString(4, "G");
            ResultSet gigResult = gigInsertStatement.executeQuery();
            System.out.println("Inserting " + 1 + " record into gig table");

            int gigID = 0;
            if (gigResult.next()){
                gigID = gigResult.getInt("gigID");
            }

            // Insert into gig_ticket table
            PreparedStatement gig_tickePreparedStatement = conn.prepareStatement("INSERT INTO gig_ticket (gigID, pricetype, price) VALUES (?,?,?)");
            gig_tickePreparedStatement.setInt(1, gigID);
            gig_tickePreparedStatement.setString(2, "A");
            gig_tickePreparedStatement.setInt(3, adultTicketPrice);

            gig_tickePreparedStatement.executeUpdate();
            System.out.println("Inserting "+ 1 + "record into gig_ticket table");

            // Insert into the act_gig table
            PreparedStatement actgigInsertStatement = conn.prepareStatement("INSERT INTO act_gig VALUES(?,?,?,?,?)");
            for (ActPerformanceDetails act: actDetails){
                actgigInsertStatement.setInt(1, act.getActID());
                actgigInsertStatement.setInt(2, gigID);
                actgigInsertStatement.setInt(3, act.getFee());
                actgigInsertStatement.setTimestamp(4, Timestamp.valueOf(act.getOnTime()));
                actgigInsertStatement.setInt(5, act.getDuration());

                actgigInsertStatement.executeUpdate();
            }
            conn.commit();
            System.out.println("Successfully added "+actDetails.length+" records into act_gig table");

        } catch(SQLException e){
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
        }

    }
    /**
     * Purchases a ticket for a given customer
     * 
     * @param conn A database connection
     * @param gigid The gigID of the gig
     * @param name Name of the customer
     * @param email Email of the customer
     * @param ticketType Could be an Adult or a Child ticket
     */

    public static void task3(Connection conn, int gigid, String name, String email, String ticketType){
        String checkGig = "SELECT * FROM gig WHERE gigID = ?";
        String checkPriceType = "SELECT price FROM gig_ticket WHERE gigID = ? AND pricetype = ?";
        try{
            conn.setAutoCommit(false);

            PreparedStatement checkGigStatement = conn.prepareStatement(checkGig);
            checkGigStatement.setInt(1, gigid);
            ResultSet gigExist = checkGigStatement.executeQuery();

            // If gig doesn't exist, abort and rollback
            if (!gigExist.next()) {
                System.err.format("Gig does not exist for gigID: %d\n", gigid);
                conn.rollback();  // Rollback any changes made in the current transaction
                return;
            }

            PreparedStatement checkPriceStatement = conn.prepareStatement(checkPriceType);
            checkPriceStatement.setInt(1, gigid);
            checkPriceStatement.setString(2, ticketType);
            ResultSet priceExist = checkPriceStatement.executeQuery();
            
            // If ticket type doesn't exist, abort and rollback
            int price = 0;
            if (!priceExist.next()) {
                System.err.format("Ticket does not exist for type: %s\n", ticketType);
                conn.rollback();  // Rollback any changes made in the current transaction
                return;
            } else {
                price = priceExist.getInt(1);
            }

            // If the gig and ticket type exist, insert customer ticket into the ticket table
            PreparedStatement insertTicketStatement = conn.prepareStatement("INSERT INTO ticket (gigID, pricetype, cost, customername, customeremail) VALUES (?,?,?,?,?)");
            insertTicketStatement.setInt(1, gigid);
            insertTicketStatement.setString(2, ticketType);
            insertTicketStatement.setInt(3, price);
            insertTicketStatement.setString(4, name);
            insertTicketStatement.setString(5, email);

            insertTicketStatement.executeUpdate();

            conn.commit();
            System.out.println("Purchased 1 ticket for "+name);


        } catch (SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            
            try {
                conn.rollback();  // Rollback in case of an error
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                conn.setAutoCommit(true);  // Reset auto commit to true after transaction
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * A function to cancel an act
     * 
     * @param conn A database connection
     * @param gigID The gigID of the gig we have to modify
     * @param actName The name of the act to cancel
     * @return a 2D array of strings containing names and email addresses of customers who have affected tickets,
               ordered by customer name (ascending alphabetical order), containing no duplicates
     */
    public static String[][] task4(Connection conn, int gigID, String actName){
        String selectActId = "SELECT actID FROM act WHERE actname = ?";
        String[][] result;
        try{
            conn.setAutoCommit(false);

            // Check if the act exists
            PreparedStatement getact = conn.prepareStatement(selectActId);
            getact.setString(1, actName);
            ResultSet actIdSet = getact.executeQuery();
            int actID = 0;
            if (!actIdSet.next()) {
                System.err.println("Act does not exist.");
                return null;  // No act found with the given name
            }
            actID = actIdSet.getInt("actID");

            // Check if the act is part of the gig
            String actTimingQuery = "SELECT ontime FROM act_gig WHERE gigID = ? AND actID = ?";
            PreparedStatement actTimingStatement = conn.prepareStatement(actTimingQuery);
            actTimingStatement.setInt(1, gigID);
            actTimingStatement.setInt(2, actID);
            ResultSet timingResult = actTimingStatement.executeQuery();

            if (!timingResult.next()) {
                System.err.println("This act is not part of the given gig.");
                return null;  // Act not found in the gig
            }

            // Check if the act is the final act (last or the only one)
            String finalQuery = "SELECT COUNT(*) FROM act_gig WHERE gigID = ? AND actID = ? AND ontime = (SELECT MAX(ontime) FROM act_gig WHERE gigID = ?)";
            PreparedStatement finalStatement = conn.prepareStatement(finalQuery);
            finalStatement.setInt(1, gigID);
            finalStatement.setInt(2, actID);
            finalStatement.setInt(3, gigID);
            ResultSet checkFinalActResult = finalStatement.executeQuery();
            checkFinalActResult.next();
            int checkFinalAct = checkFinalActResult.getInt(1);
            
            if (checkFinalAct == 1){
                // Cancel the gig
                String cancelGigQuery = "UPDATE gig SET gigstatus = 'C' WHERE gigID = ?";
                PreparedStatement cancelGigStatement = conn.prepareStatement(cancelGigQuery);
                cancelGigStatement.setInt(1, gigID);
                cancelGigStatement.executeUpdate();
                
                // Update the ticket prices to 0
                String updateTicketsQuery = "UPDATE ticket SET cost = 0 WHERE gigID = ?";
                PreparedStatement updateTicketsStatement = conn.prepareStatement(updateTicketsQuery);
                updateTicketsStatement.setInt(1, gigID);
                updateTicketsStatement.executeUpdate();

                System.out.println("Gig " + gigID + " has been canceled.");
            }
            else{
                // If not the final act, remove the act and adjust subsequent act timings
                String removeActQuery = "DELETE FROM act_gig WHERE gigID = ? AND actID = ?";
                PreparedStatement removeActStatement = conn.prepareStatement(removeActQuery);
                removeActStatement.setInt(1, gigID);
                removeActStatement.setInt(2, actID);
                removeActStatement.executeUpdate();

                // Adjust timings of subsequent acts
                String adjustTimingQuery = "UPDATE act_gig SET ontime = ontime - INTERVAL '1 hour' WHERE gigID = ? AND ontime > ?";
                PreparedStatement adjustTimingStatement = conn.prepareStatement(adjustTimingQuery);
                adjustTimingStatement.setInt(1, gigID);
                adjustTimingStatement.setTimestamp(2, timingResult.getTimestamp("ontime"));
                adjustTimingStatement.executeUpdate();

                System.out.println("Act " + actName + " has been removed from the gig.");
            }
            // Return customer details for affected tickets
            String getAffectedCustomersQuery = "SELECT DISTINCT customername, customeremail FROM ticket WHERE gigID = ? AND cost = 0 ORDER BY customername";
            PreparedStatement customerStatement = conn.prepareStatement(getAffectedCustomersQuery);
            customerStatement.setInt(1, gigID);
            ResultSet customerResult = customerStatement.executeQuery();
            
            System.out.println("Affected customers:");
            result = convertResultToStrings(customerResult);
            printTable(result);

            conn.commit();
            return result;

        } catch (SQLException e){
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            
            try {
                conn.rollback();  // Rollback in case of an error
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                conn.setAutoCommit(true);  // Reset auto commit to true after transaction
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Finds how many more tickets (of the cheapest ticket price for that gig) still need to be sold for
the promoters to, at least, be able to pay all the agreed fees (as listed in the act_gig table, not from the
act's standard fee) for the acts and to pay the venue fee.
     * @param conn A connection to the database
     * @return 2D array containing the gigID and tickets to sell
     */
    public static String[][] task5(Connection conn){
        try{
            String query = 
            "WITH filtered_act_gig AS (SELECT ag.gigID, ag.actID, ag.actgigfee, ROW_NUMBER() OVER (PARTITION BY ag.gigID, ag.actID ORDER BY ag.ontime) AS rn FROM act_gig ag), act_gig_cost AS (SELECT gigID, SUM(actgigfee) AS total_act_gig_fee FROM filtered_act_gig WHERE rn = 1 GROUP BY gigID), venue_cost AS (SELECT g.gigID, v.hirecost FROM gig g JOIN venue v ON g.venueID = v.venueID), ticket_cost AS (SELECT g.gigID, COALESCE(SUM(t.cost), 0) AS total_ticket_cost FROM gig g LEFT JOIN ticket t ON g.gigID = t.gigID GROUP BY g.gigID), min_price AS (SELECT gigID, MIN(price) AS minimum_price FROM gig_ticket GROUP BY gigID) SELECT g.gigID, (agc.total_act_gig_fee + vc.hirecost - tc.total_ticket_cost) / mp.minimum_price AS total_cost_without_bought_tickets FROM gig g JOIN act_gig_cost agc ON g.gigID = agc.gigID JOIN venue_cost vc ON g.gigID = vc.gigID JOIN ticket_cost tc ON g.gigID = tc.gigID JOIN min_price mp ON g.gigID = mp.gigID ORDER BY g.gigID";
            PreparedStatement queryPreparedStatement = conn.prepareStatement(query);
            ResultSet resultSet = queryPreparedStatement.executeQuery();
            String[][] result = convertResultToStrings(resultSet);
            System.out.println("Tickets needed to sell for each gig");
            printTable(result);
            return result;
            
        } catch(SQLException e){
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            
            try {
                conn.rollback();  // Rollback in case of an error
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                conn.setAutoCommit(true);  // Reset auto commit to true after transaction
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Creates a 2-dimensional array of strings to show the total number of tickets (of any pricetype) that each act
has sold.
     * @param conn A connection to the database
     * @return 2D array containing the actname, year and total tickets sold.
     */
    public static String[][] task6(Connection conn){
        try{
            String query =
            "WITH ticket_counts AS (SELECT a.actname, EXTRACT(YEAR FROM g.gigdatetime)::TEXT AS year, COUNT(t.ticketID) AS total_tickets_sold FROM gig g JOIN act_gig ag ON g.gigID = ag.gigID JOIN act a ON ag.actID = a.actID LEFT JOIN ticket t ON g.gigID = t.gigID WHERE ag.ontime = (SELECT MAX(ontime) FROM act_gig WHERE gigID = g.gigID) AND g.gigstatus != 'C' GROUP BY a.actname, year HAVING COUNT(t.ticketID) > 0 UNION ALL SELECT a.actname, 'Total' AS year, COUNT(t.ticketID) AS total_tickets_sold FROM gig g JOIN act_gig ag ON g.gigID = ag.gigID JOIN act a ON ag.actID = a.actID LEFT JOIN ticket t ON g.gigID = t.gigID WHERE ag.ontime = (SELECT MAX(ontime) FROM act_gig WHERE gigID = g.gigID) AND g.gigstatus != 'C' GROUP BY a.actname HAVING COUNT(t.ticketID) > 0), total_counts AS (SELECT actname, MAX(total_tickets_sold) AS total_sold FROM ticket_counts WHERE year = 'Total' GROUP BY actname) SELECT tc.actname, tc.year, tc.total_tickets_sold FROM ticket_counts tc JOIN total_counts tc_total ON tc.actname = tc_total.actname ORDER BY tc_total.total_sold, tc.actname, CASE WHEN tc.year = 'Total' THEN 1 ELSE 0 END, tc.year";
            PreparedStatement queryPreparedStatement = conn.prepareStatement(query);
            ResultSet resultSet = queryPreparedStatement.executeQuery();
            String[][] result = convertResultToStrings(resultSet);
            System.out.println("Tickets sold per act");
            printTable(result);
            return result;

        } catch(SQLException e){
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            
            try {
                conn.rollback();  // Rollback in case of an error
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                conn.setAutoCommit(true);  // Reset auto commit to true after transaction
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Creates a 2D array of strings that shows each act who has ever performed a gig as a headline act (the final or
only act) along with the names of customers who have attended at least one of these gigs per calendar year
(if the act performed such a gig as a headline act in that year).
     * @param conn A connection to the database
     * @return 2D array containing the actname and customer name.
     */
    public static String[][] task7(Connection conn){
        try{
            String query =
            "WITH headline_gigs AS (SELECT a.actname, g.gigID, EXTRACT(YEAR FROM g.gigdatetime) AS year FROM gig g JOIN act_gig ag ON g.gigID = ag.gigID JOIN act a ON ag.actID = a.actID WHERE ag.ontime = (SELECT MAX(ontime) FROM act_gig WHERE gigID = g.gigID) AND g.gigstatus != 'C'), customer_per_year AS (SELECT hg.actname, t.customername, EXTRACT(YEAR FROM g.gigdatetime) AS year FROM headline_gigs hg JOIN ticket t ON hg.gigID = t.gigID JOIN gig g ON t.gigID = g.gigID GROUP BY hg.actname, t.customername, EXTRACT(YEAR FROM g.gigdatetime)), valid_customers AS (SELECT cp.actname, cp.customername FROM customer_per_year cp JOIN (SELECT actname, COUNT(DISTINCT year) AS headline_years FROM headline_gigs GROUP BY actname) hg_counts ON cp.actname = hg_counts.actname GROUP BY cp.actname, cp.customername HAVING COUNT(DISTINCT cp.year) = MAX(hg_counts.headline_years)), acts_without_customers AS (SELECT DISTINCT actname, '[None]' AS customername FROM headline_gigs WHERE actname NOT IN (SELECT actname FROM valid_customers)) SELECT actname, customername FROM valid_customers UNION ALL SELECT actname, customername FROM acts_without_customers ORDER BY actname, customername";
            PreparedStatement queryPreparedStatement = conn.prepareStatement(query);
            ResultSet resultSet = queryPreparedStatement.executeQuery();
            String[][] result = convertResultToStrings(resultSet);
            System.out.println("Tickets sold per act");
            printTable(result);
            return result;

        } catch(SQLException e){
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            
            try {
                conn.rollback();  // Rollback in case of an error
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                conn.setAutoCommit(true);  // Reset auto commit to true after transaction
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Returns economically feasible gigs to organise with only a single act
     * @param conn A connection to the database
     * @return A 2D array containing the venuename, actname as well as the tickets required to reach to cover all the costs
     */
    public static String[][] task8(Connection conn){
        try{
            String query =
            "WITH global_average_ticket_price AS (SELECT COALESCE(ROUND(AVG(t.cost)), 0) AS avg_ticket_price FROM ticket t), economically_feasible AS (SELECT v.venuename, a.actname, CEIL((a.standardfee + v.hirecost) / NULLIF(global.avg_ticket_price, 1)) AS tickets_required FROM venue v CROSS JOIN act a CROSS JOIN global_average_ticket_price global WHERE global.avg_ticket_price > 0 AND CEIL((a.standardfee + v.hirecost) / NULLIF(global.avg_ticket_price, 1)) <= v.capacity) SELECT * FROM economically_feasible ORDER BY venuename, tickets_required DESC";                                
            PreparedStatement queryPreparedStatement = conn.prepareStatement(query);
            ResultSet resultSet = queryPreparedStatement.executeQuery();
            String[][] result = convertResultToStrings(resultSet);
            System.out.println("Tickets sold per act");
            printTable(result);
            return result;

        } catch(SQLException e){
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            
            try {
                conn.rollback();  // Rollback in case of an error
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                conn.setAutoCommit(true);  // Reset auto commit to true after transaction
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Prompts the user for input
     * @param prompt Prompt for user input
     * @return the text the user typed
     */
    private static String readEntry(String prompt) {
        
        try {
            StringBuffer buffer = new StringBuffer();
            System.out.print(prompt);
            System.out.flush();
            int c = System.in.read();
            while(c != '\n' && c != -1) {
                buffer.append((char)c);
                c = System.in.read();
            }
            return buffer.toString().trim();
        } catch (IOException e) {
            return "";
        }

    }
     
    /**
    * Gets the connection to the database using the Postgres driver, connecting via unix sockets
    * @return A JDBC Connection object
    */
    public static Connection getSocketConnection(){
        Properties props = new Properties();
        props.setProperty("socketFactory", "org.newsclub.net.unix.AFUNIXSocketFactory$FactoryArg");
        props.setProperty("socketFactoryArg",System.getenv("HOME") + "/cs258-postgres/postgres/tmp/.s.PGSQL.5432");
        Connection conn;
        try{
          conn = DriverManager.getConnection("jdbc:postgresql://localhost/cwk", props);
          return conn;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gets the connection to the database using the Postgres driver, connecting via TCP/IP port
     * @return A JDBC Connection object
     */
    public static Connection getPortConnection() {
        
        String user = "postgres";
        String passwrd = "password";
        Connection conn;

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException x) {
            System.out.println("Driver could not be loaded");
        }

        try {
            conn = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/cwk?user="+ user +"&password=" + passwrd);
            return conn;
        } catch(SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            System.out.println("Error retrieving connection");
            return null;
        }
    }

    /**
     * Iterates through a ResultSet and converts to a 2D Array of Strings
     * @param rs JDBC ResultSet
     * @return 2D Array of Strings
     */
     public static String[][] convertResultToStrings(ResultSet rs) {
        List<String[]> output = new ArrayList<>();
        String[][] out = null;
        try {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                String[] thisRow = new String[columns];
                for (int i = 0; i < columns; i++) {
                    thisRow[i] = rs.getString(i + 1);
                }
                output.add(thisRow);
            }
            out = new String[output.size()][columns];
            for (int i = 0; i < output.size(); i++) {
                out[i] = output.get(i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static void printTable(String[][] out){
        int numCols = out[0].length;
        int w = 20;
        int widths[] = new int[numCols];
        for(int i = 0; i < numCols; i++){
            widths[i] = w;
        }
        printTable(out,widths);
    }

    public static void printTable(String[][] out, int[] widths){
        for(int i = 0; i < out.length; i++){
            for(int j = 0; j < out[i].length; j++){
                System.out.format("%"+widths[j]+"s",out[i][j]);
                if(j < out[i].length - 1){
                    System.out.print(",");
                }
            }
            System.out.println();
        }
    }

}
