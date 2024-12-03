# Gig_Application
This respository contains an application which allows users to create/organize and book gigs/ acts for an event. This app has been developed using Java and SQL

# Design Choices

1. To make the gigstatus more consistent we can utilize the ENUM type and provide it with predefined values, in order to ensure no error's occur
Example:
```
CREATE TYPE gig_status AS ENUM ('A', 'C');  -- 'A' for Active, 'C' for Cancelled

ALTER TABLE gig
ADD COLUMN gigstatus gig_status NOT NULL;
```
We won't be able to do this for attributes like genre since they are not restricted to a few.

2. Most of the triggers in the schema.sql could be written in a simpler format if we include a composite index on act_gig for performance optimization. This can be added to the attributes actID and gigID as follows:

```
CREATE INDEX idx_act_gig_gigid_actid ON act_gig (gigID, actID);
```

3. In order to calculate the total tickets sold for every gig we could have another table called ticket_sales since it helps us calculate the revenue for each gig in an easier way.

```
CREATE TABLE ticket_sales (
    gigID INTEGER PRIMARY KEY,
    total_tickets_sold INTEGER DEFAULT 0,
    total_revenue INTEGER DEFAULT 0,
    FOREIGN KEY (gigID) REFERENCES gig (gigID)
);

```

4. We could include another table which contains details about each customer so that it becomes much easier to process it in the ticket relation. Therefore it could be simplified in this format:

```
-- Create the customer table with unique email constraint
CREATE TABLE customer (
    customerID SERIAL PRIMARY KEY,
    customername VARCHAR(100),
    customeremail VARCHAR(100) UNIQUE  -- Ensure email is unique across all customers
);

-- Create the ticket table, referencing customerID
CREATE TABLE ticket (
    ticketID SERIAL PRIMARY KEY,
    gigID INTEGER,
    customerID INTEGER,  -- Foreign key to customer table
    pricetype VARCHAR(2),
    cost INTEGER CHECK(cost >= 0),
    FOREIGN KEY (gigID) REFERENCES gig (gigID),
    FOREIGN KEY (customerID) REFERENCES customer (customerID)  -- Ensures customer is linked to ticket
);
```

# Task Implementations

## Task 1
For **Task 1: Gig Line-Up**, the goal was to retrieve the details of acts performing at a particular gig and present them in a table-like format with columns for "Act Name," "On Time," and "Off Time." The solution was implemented by executing an SQL query to fetch the relevant data.

**The SQL query is designed to:**

1. Select the act name from the act table and the start time (On Time) and end time (Off Time) of each act from the act_gig table.
2. The `TO_CHAR` function is used to format the times in 24-hour clock format without seconds.
3. The `Off Time` is computed by adding the duration of each act (in minutes) to the ontime.
4. The query ensures that only acts linked to the given gigID are selected.
5. The results are ordered in ascending order of ontime so that they are displayed in the order they will occur during the gig.

Finally, the convertResultToStrings() method is used to convert the ResultSet into a two-dimensional array of strings, which is then returned as the final output. The solution ensures clarity and simplicity in retrieving the details of each act, making it easy to understand the line-up for the given gig.

## Task 2
For **Task 2: Organising a Gig**, the task was to create a new gig at a given venue with multiple acts and ensure that all data is inserted correctly, respecting any business rules and preserving database integrity.

**Steps Implemented**:

1. **Database Transaction Management**: The code begins by setting AutoCommit to false to ensure that all database insertions are treated as a single transaction. This ensures that if anything fails, all changes are rolled back to maintain database integrity.

2. **Venue Identification**: A venueID is obtained by querying the venue table with the given venue name. This ensures that the gig is set up at a valid location.


3. **Inserting Gig Data**: A new entry is inserted into the gig table first since it forms the basis of every other table. The RETURNING gigID clause is used to retrieve the automatically generated gigID, which is used in subsequent insertions.

4. **Ticket Price Insertion**: The ticket price for the gig is inserted into the gig_ticket table using the retrieved gigID. This includes the price type (adult tickets) and the ticket price provided.

5. **Inserting Act Details**: For each act, details like actID, fee, on-time, and duration are inserted from the act_details list into the act_gig table using a prepared statement. This loop ensures all acts are properly linked to the created gig.

6. **Committing the Transaction**: After all insertions are complete, the transaction is committed using conn.commit(). If any part of the insertion fails, the transaction is rolled back to ensure consistency.

7. **Business Rule Validation**: The code takes measures to ensure that the gig setup adheres to any business rules. If any insert fails, the use of a transaction ensures that the entire gig creation is aborted, preserving database integrity.

## Task 3
For **Task 3: Booking a Ticket**, the goal was to allow customers to buy tickets for a specific gig, ensuring the details provided are valid. The process includes checking the gig's existence, verifying the ticket type, and finally completing the purchase transaction if all criteria are met.

**Steps Implemented**:

1. **Begin Database Transaction**:
   - `AutoCommit` is set to `false` to begin a transaction. This ensures that any operations that fail will cause the entire transaction to roll back, maintaining database consistency.

2. **Checking Gig Existence**:
   - A PreparedStatement (`checkGig`) is used to query the `gig` table with the provided gigID. If no gig exists with the given gigID, an error message is printed, and the transaction is rolled back to ensure no changes are made to the database.

3. **Validating Ticket Type**:
   - If the gig exists, another PreparedStatement (`checkPriceType`) is used to check if the given ticket type exists for the provided **gigID** in the `gig_ticket` table. If no such ticket type exists, the transaction is rolled back and an error message is printed.

4. **Inserting Ticket Purchase**:
   - If both the gig and the ticket type are valid, a ticket purchase entry is added to the `ticket` table using a PreparedStatement (`insertTicketStatement`). 
   - The ticket price is fetched from the `gig_ticket` table.

5. **Committing the Transaction**:
   - After the ticket is inserted successfully, `conn.commit()` is called to make the changes permanent.

6. **Rollback on Error**:
   - If any exception occurs during the process, the transaction is rolled back using `conn.rollback()`. This ensures that no partial data is inserted or inconsistent data is left in the database.

7. **Resetting AutoCommit**:
   - Finally, AutoCommit is set back to `true` to ensure future transactions behave normally.

**Business Rule Validation**:
- The code takes care to validate that both the `gig` and the `ticket type` are present before allowing a purchase. If either validation fails, the entire operation is aborted and rolled back.
- This guarantees that no tickets are sold for nonexistent gigs or invalid ticket types, maintaining data integrity.

## Task 4
For **Task 4: Cancelling an Act**, the aim is to cancel a specific act within a gig and make necessary adjustments to maintain proper schedules for the remaining acts, or to cancel the entire gig if required.

**Steps Implemented**:

1. **Begin Transaction**:
   - `AutoCommit` is set to `false` to begin a transaction, ensuring the operations are atomic. If an error occurs, the entire transaction is rolled back.

2. **Verify Act Existence**:
   - The code first checks if the given actName exists in the `act` table by using a PreparedStatement (`selectActId`). If the act doesn't exist, the function returns `null` without making any changes.

3. **Verify Act in Gig**:
   - After verifying the existence of the act, the code checks if the specified act is part of the given gigID (`actTimingQuery`). If not, it returns `null`.

4. **Check if the Act is Final in Gig**:
   - The code checks if the given act is the last or only performance in the gig (`finalQuery`). If it is, the entire gig is cancelled. Otherwise, the function proceeds to remove the specific act and adjust the timings of subsequent acts.

5. **Cancel Gig if Required**:
   - If the act is the final performance, the gig is cancelled by setting `gigstatus` to `'C'` (`cancelGigQuery`).
   - All tickets for the cancelled gig have their cost set to `0` (`updateTicketsQuery`).

6. **Remove Act if Not Final**:
   - If the act isn't the final one, it is removed from the `act_gig` table (`removeActQuery`).
   - The timings of subsequent acts are adjusted (`adjustTimingQuery`) to ensure there is no gap left by the cancelled performance.

7. **Get Affected Customers**:
   - The final part of the code retrieves the details of customers whose tickets are affected (`getAffectedCustomersQuery`). These customers are filtered to ensure they are returned in ascending alphabetical order, with no duplicates.

8. **Commit or Rollback**:
   - If all operations are successful, `conn.commit()` is called to save the changes.
   - In case of an error, `conn.rollback()` is executed to revert all changes made during the transaction, ensuring the database remains consistent.

9. **Return Customer Details**:
   - The function finally returns a 2D array of customer names and email addresses (`result`) to indicate which customers were affected by the cancellation.

**Business Rules and Constraints**:
- The function ensures that the schedule integrity is maintained for all subsequent acts after an act cancellation, preserving the business rules regarding intervals.
- The use of transactions guarantees the correctness of data and prevents partial updates from affecting the database.

This implementation provides flexibility by handling different scenarios (cancelling an entire **gig** vs. a specific **act**), ensuring the correct update of related data.

## Task 5
For **Task 5: Tickets Needed to Sell**, the goal was to determine how many tickets still need to be sold for each gig to cover all expenses, including act fees and venue costs. The solution calculates this amount for each gig and then determines the number of tickets required at the minimum ticket price.

**Steps Implemented**:

1. **Common Table Expressions (CTEs) with the Query**:
   - **`filtered_act_gig`**: This CTE is used to handle multiple performances by the same act in a gig. It uses `ROW_NUMBER()` to select only the first occurrence of an act performing at a given gig, ensuring that no act's fee is counted more than once for each gig.
   - **`act_gig_cost`**: After filtering out duplicate acts, this CTE calculates the **total act fees** (`total_act_gig_fee`) for each gig by summing the **actgigfee**.
   - **`venue_cost`**: Retrieves the **hirecost** for each gig by joining the `gig` table with the `venue` table.
   - **`ticket_cost`**: Calculates the **total cost of tickets sold** for each gig by joining the `ticket` table with the `gig` table and using `COALESCE` to handle any gigs where no tickets have been sold (defaults to `0`).
   - **`min_price`**: Determines the **minimum ticket price** for each gig from the `gig_ticket` table.

2. **Final Calculation**:
   - The final part of the query calculates the **remaining tickets to be sold** by dividing the **remaining costs** (`total_act_gig_fee + hirecost - total_ticket_cost`) by the **minimum ticket price** (`minimum_price`).
   - The result is sorted by `gigID`.

3. **Using a Prepared Statement**:
   - A **PreparedStatement** is used to execute the query and fetch the result set.
   - The **`convertResultToStrings`** method is used to convert the **ResultSet** to a 2D array of strings, which is returned and also printed using `printTable()`.

4. **Exception Handling**:
   - If an SQL exception occurs, the code prints the error details and **rolls back** the transaction, ensuring no unintended changes are made to the database.
   - The transaction is also reset to `AutoCommit` to `true` after completion.

5. **Business Rules**:
   - The number of tickets to sell is calculated based on the **minimum ticket price**, ensuring that the promoters cover the total expenses at the cheapest rate possible.
   - The query also ensures that only the necessary costs are considered for each gig—meaning only those gigs that have not yet sold enough tickets to cover the expenses are displayed, with the rest showing `0` tickets required.

## Task 6
For **Task 6: How Many Tickets Sold**, the aim was to generate a report that displays the total number of tickets sold by each act per year when they were listed as the headline act (final or only act) in a gig. Only gigs that haven't been canceled were considered. The report also includes a summary ("Total") for each act and is sorted with the least sold first.

**Steps Implemented**:

1. **Common Table Expressions (CTEs) and Aggregations**:
   - **`ticket_counts`**: This CTE is responsible for calculating the **number of tickets sold per year** for each act when they performed as the **headline act**.
     - It joins the `gig`, `act_gig`, `act`, and `ticket` tables.
     - Only the final or only act in each gig (`ag.ontime = MAX(ontime)`) is considered.
     - Gigs marked as canceled (`g.gigstatus = 'C'`) are excluded.
     - The CTE also calculates the **"Total"** count for each act across all years.
   - **`total_counts`**: This CTE extracts the **total tickets sold** per act for sorting purposes. It is used to ensure that acts with the **least total tickets** sold appear first.

2. **Final Selection**:
   - The main part of the query retrieves data from `ticket_counts` and joins it with `total_counts` to order the final result appropriately.
   - The ordering is designed to:
     - Sort by the **total number of tickets sold** for each act, starting with the lowest.
     - Ensure that the acts are ordered by their name alphabetically.
     - Place the "Total" row after the individual yearly records for each act.

3. **Query Execution and Result Processing**:
   - A PreparedStatement is used to execute the query and fetch the results into a ResultSet.
   - The `convertResultToStrings` method converts the `ResultSet` into a 2D array of strings, which is returned and printed using `printTable()`.

4. **Exception Handling**:
   - If an SQL exception occurs, the code rolls back any modifications made in the current transaction to maintain the integrity of the database.
   - Finally, it resets AutoCommit to true to resume normal transaction behavior.

5. **Business Rules**:
   - The query takes into consideration only gigs that are not canceled.
   - Only those acts that were listed as the final or only act in the gig are considered, which aligns with the definition of a **headline act**.
   - The result includes both the yearly count of tickets sold and the total tickets sold by each act, providing a clear summary for analysis.

## Task 7
For **Task 7: Regular Customers**, the objective was to list all acts that have performed as a **headline act** in gigs, along with customers who attended such gigs for every year that the act performed as a headline act. The solution also needed to handle cases where an act had no customers meeting the requirements by listing `[None]` in the Customer Name column.

**Steps Implemented**:

1. **Common Table Expressions (CTEs)**:
   - **`headline_gigs`**: This CTE identifies gigs where an act performed as a **headline act** (the final or only act). Only gigs that are not canceled are considered.
     - The query joins the `gig`, `act_gig`, and `act` tables and filters based on the `MAX(ontime)` condition to determine the final or only act in a gig.
   - **`customer_per_year`**: This CTE finds the customers who attended gigs where the acts performed as the headline act.
     - This is done by joining the `headline_gigs` CTE with the `ticket` table to determine which customers attended the headline gigs.
     - Grouping is done by `actname`, `customername`, and the extracted year to ensure that customers who attended gigs per year are considered.
   - **`valid_customers`**: This CTE determines the valid customers who have attended at least one gig per year in which the act performed as a headline act.
     - It uses the `customer_per_year` CTE and compares it with the total number of years that an act has been a headline act (`headline_years`).
     - The `GROUP BY` and `HAVING` clause ensures only those customers are considered who attended at least one headline gig per calendar year of the headline act.
   - **`acts_without_customers`**: This CTE lists acts that have no valid customers by inserting `[None]` as the **Customer Name**.
     - It selects distinct acts from `headline_gigs` that are not present in the `valid_customers` CTE.

2. **Final Selection**:
   - The main part of the query **unions** the `valid_customers` CTE and the `acts_without_customers` CTE.
   - This combination ensures that every headline act is listed, including those with `[None]` if no valid customers are available.
   - The result is ordered alphabetically by Act Name and Customer Name.

3. **Query Execution and Result Processing**:
   - The query is executed using a PreparedStatement and the result is converted into a 2D array of strings using the `convertResultToStrings` method.
   - The resulting 2D array is printed using `printTable()`.

4. **Exception Handling**:
   - In case of an SQL exception, the program **rolls back** any changes made during the current transaction to maintain database consistency.
   - The **AutoCommit** is reset to `true` to return to normal transaction handling after completion.

5. **Business Rules**:
   - The query filters only those gigs that are not canceled (`gigstatus != 'C'`).
   - Only customers who attended gigs per year of the act's headline performances are included.
   - Acts with no valid customers are still listed, but their Customer Name is replaced with `[None]`.

## Task 8
For **Task 8: Economically Feasible Gigs**, the objective was to determine which acts could be hosted at which venues such that the cost of hosting could be covered by selling tickets at an average ticket price. The aim is to make sure that festival organizers can **break even** when selecting a venue and an act.

**Steps Implemented**:

1. **Common Table Expressions (CTEs)**:
   - **`global_average_ticket_price`**: This CTE calculates the **average ticket price** across all tickets sold (excluding gigs that were canceled).
     - The **average cost** of tickets is calculated using the `AVG()` function on the `cost` of tickets. The `COALESCE()` function is used to provide a default value of 0 in case there are no tickets.
     - The average value is then rounded to the nearest pound using the `ROUND()` function.
   
2. **Economically Feasible Gigs Calculation**:
   - **`economically_feasible`**: This CTE identifies all possible combinations of **venues and acts** and calculates the minimum number of tickets required to break even for each combination.
     - **Cross Join** (`CROSS JOIN`): The `CROSS JOIN` is used between venue and act to generate all possible combinations of venues and acts. Another cross join is used with the `global_average_ticket_price` CTE to apply the global average price to each combination.
     - The **total cost** of hosting is calculated by summing the standard fee of the act and the hire cost of the venue.
     - The **number of tickets required** is calculated as `CEIL((a.standardfee + v.hirecost) / NULLIF(global.avg_ticket_price, 1))`. The `CEIL()` function ensures that the ticket count is always a whole number (since we can't sell half a ticket). The `NULLIF()` function is used to avoid division by zero by substituting a `NULL` value if the average ticket price is `1`.
     - **Feasibility Condition**: The result set includes only those combinations where the calculated **number of tickets required** is less than or equal to the **capacity** of the venue (`tickets_required <= v.capacity`). This ensures that the venue can accommodate the audience needed to cover costs.

3. **Final Selection**:
   - The final selection fetches all columns from the `economically_feasible` CTE.
   - The output is ordered by venue name in alphabetical order and by the number of tickets required in descending order (`ORDER BY venuename, tickets_required DESC`). This means that for each venue, the act that requires the most tickets will be listed first.

4. **Query Execution and Result Processing**:
   - The query is executed using a PreparedStatement and the result is converted into a 2D array of strings using the `convertResultToStrings()` method.
   - The result is printed using `printTable()`.

5. **Business Rules**:
   - Only economically feasible gigs where the average ticket price is positive are considered (`global.avg_ticket_price > 0`).
   - The number of tickets required must not exceed the venue capacity to ensure that the break-even point is achievable within the limitations of the venue.

6. **Exception Handling**:
   - Any SQL exceptions will result in the transaction being rolled back to maintain database consistency.
   - The **AutoCommit** is reset to `true` to ensure subsequent transactions are committed automatically after the completion of the method.
