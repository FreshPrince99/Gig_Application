DROP TABLE IF EXISTS act CASCADE;
DROP TABLE IF EXISTS act_gig CASCADE;
DROP TABLE IF EXISTS gig CASCADE;
DROP TABLE IF EXISTS gig_ticket CASCADE;
DROP TABLE IF EXISTS ticket CASCADE;
DROP TABLE IF EXISTS venue CASCADE;

/* Table containing the list of acts */
CREATE TABLE act (
    actID SERIAL PRIMARY KEY,
    actname VARCHAR(100) UNIQUE,
    genre VARCHAR(10) NOT NULL,
    standardfee INTEGER CHECK(standardfee >= 0)
);

/* Table containing the list of venues*/
CREATE TABLE venue (
    venueID SERIAL PRIMARY KEY,
    venuename VARCHAR(100) ,
    hirecost INTEGER CHECK(hirecost >= 0),
    capacity INTEGER
);

/* Table containing the list of gigs */
CREATE TABLE gig (
    gigID SERIAL PRIMARY KEY,
    venueID INTEGER NOT NULL,
    gigtitle VARCHAR(100),
    gigdatetime TIMESTAMP CHECK(gigdatetime::TIME >= '09:00:00' AND gigdatetime::TIME <= '23:59:00'), -- # Rule 12 --
    gigstatus VARCHAR(1),
    foreign key(venueID) references venue(venueID)
);

/* Table containing the list of acts at a certain gig */
CREATE TABLE act_gig (
    actID INTEGER NOT NULL,
    gigID INTEGER NOT NULL,
    actgigfee INTEGER NOT NULL CHECK(actgigfee >= 0),
    ontime TIMESTAMP NOT NULL,
    duration INTEGER CHECK(duration>= 15 and duration <= 90), /* Limits the duration between this range */
    foreign key(actID) references act (actID),
    foreign key(gigID) references gig (gigID),
    constraint unique_act_gig_ontime UNIQUE (actID, gigID, ontime) /* Rule 1,2 and 3. This ensures that no act performs at different gigs at the same time*/
);

/* Table containing the ticket prices for each gig */
CREATE TABLE gig_ticket (
    gigID INTEGER NOT NULL,
    pricetype VARCHAR(2) NOT NULL,
    price INTEGER CHECK(price >= 0),
    foreign key(gigID) references gig (gigID)
);

/* Table containing details of a ticket that has been purchased */ 
CREATE TABLE ticket (
    ticketID SERIAL PRIMARY KEY,
    gigID INTEGER,
    pricetype VARCHAR(2),
    cost INTEGER CHECK(cost >= 0),
    customername VARCHAR(100),
    customeremail VARCHAR(100),
    foreign key(gigID) references gig (gigID)
);

/* TRIGGERS */

-- # Trigger to ensure emails are unique --
CREATE OR REPLACE FUNCTION unique_emails() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS(
        SELECT 1
        FROM ticket
        WHERE customeremail = NEW.customeremail
        AND customername != NEW.customername
    ) THEN
        RAISE EXCEPTION 'No two people can have the same email';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_unique_emails
BEFORE INSERT OR UPDATE ON ticket
FOR EACH ROW
EXECUTE FUNCTION unique_emails();

-- # Rule 1 : Trigger to ensure there is no overlap between acts at the same gig -- 
CREATE OR REPLACE FUNCTION prevent_overlap() RETURNS TRIGGER AS $$
BEGIN
    -- Check if there is any performance that overlaps with another performance --
    IF EXISTS(
        SELECT 1
        FROM act_gig
        WHERE gigID = NEW.gigID
        AND (
            (NEW.ontime >= ontime AND NEW.ontime < (ontime + INTERVAL '1 minute' * duration)) OR
            (NEW.ontime + INTERVAL '1 minute' * NEW.duration > ontime AND NEW.ontime + INTERVAL '1 minute' * NEW.duration <= (ontime + INTERVAL '1 minute' * duration)) OR
            (NEW.ontime <= ontime AND NEW.ontime + INTERVAL '1 minute' * NEW.duration > ontime)
        )
    ) THEN 
        RAISE EXCEPTION 'Act cannot overlap with another act at this gig';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_no_overlap
BEFORE INSERT OR UPDATE ON act_gig
FOR EACH ROW
EXECUTE FUNCTION prevent_overlap();

-- # Rule 2 : Trigger to ensure acts do not perform at multiple gigs at the same time -- 
CREATE OR REPLACE FUNCTION prevent_multiple_performances() RETURNS TRIGGER AS $$
BEGIN
    -- Check if there is any act that overlaps with the same act at another gig --
    IF EXISTS(
        SELECT 1
        FROM act_gig
        WHERE gigID != NEW.gigID
        AND actID = NEW.actID
        AND NEW.ontime >= (ontime)
        AND NEW.ontime <= (ontime + INTERVAL '1 minute' *duration)
    ) THEN 
        RAISE EXCEPTION 'An act cannot perform at multiple gigs at the same time';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_no_multiple_performances
BEFORE INSERT OR UPDATE ON act_gig
FOR EACH ROW
EXECUTE FUNCTION prevent_multiple_performances();

-- # Rule 3: Trigger to ensure actgigfee remains the same for each act at a gig --
CREATE OR REPLACE FUNCTION check_fee_consistency() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM act_gig
        WHERE actID = NEW.actID
        AND gigID = NEW.gigID
        AND actgigfee != NEW.actgigfee
    ) THEN
        RAISE EXCEPTION 'The actgigfee must be the same for this act at this gig';
    END IF;
        RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_fee_consistency
BEFORE INSERT OR UPDATE ON act_gig
FOR EACH ROW
EXECUTE FUNCTION check_fee_consistency();

-- # Rule 4: Trigger to ensure that the same act does not perform twice without a break --
CREATE OR REPLACE FUNCTION check_intervals_between_similar_acts() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM act_gig
        WHERE actID = NEW.actID
        AND gigID = NEW.gigID
        AND NEW.ontime < (ontime + INTERVAL '1 minute' *duration + INTERVAL '10 minute') /* This returns the start_time + duraion = end_time and adds the 10 minute interval for the break*/
    ) THEN
        RAISE EXCEPTION 'No two similar acts can be performed back to back at the same gig without an interval';
    END IF;
        RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- # Rule 5: Trigger to ensure 60-minute break between gigs for the same act
CREATE OR REPLACE FUNCTION check_travel_time() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM act_gig
        WHERE actID = NEW.actID
        AND gigID != NEW.gigID
        AND DATE(NEW.ontime) = DATE(ontime)  -- Same day check
        AND NEW.ontime < (ontime + INTERVAL '1 minute' *duration + INTERVAL '1 hour') -- 60-minute gap check
    ) THEN
        RAISE EXCEPTION 'Act must have at least a 60-minute break between gigs on the same day';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_travel_time
BEFORE INSERT OR UPDATE ON act_gig
FOR EACH ROW
EXECUTE FUNCTION check_travel_time();

-- # Rule 6: Trigger to ensure there is a 180 minute interval between gigs at a venue --
CREATE OR REPLACE FUNCTION check_gig_interval() RETURNS TRIGGER AS $$
DECLARE
    latest_end_time TIMESTAMP;
    earliest_start_time TIMESTAMP;
BEGIN 
    -- Get the latest end time of the previous gig at the same venue on the same day
    SELECT MAX(ag.ontime + (ag.duration * INTERVAL '1 minute')) 
    INTO latest_end_time
    FROM gig g
    JOIN act_gig ag ON g.gigID = ag.gigID
    WHERE g.venueID = NEW.venueID
      AND g.gigdatetime::DATE = NEW.gigdatetime::DATE
      AND g.gigdatetime < NEW.gigdatetime;

    -- Check if the gap between the previous gig end and the new gig is less than 180 minutes
    IF latest_end_time IS NOT NULL AND latest_end_time + INTERVAL '180 minutes' > NEW.gigdatetime THEN
        RAISE EXCEPTION 'Venue must have at least a 180-minute gap between gigs on the same day';
    END IF;

    -- Get the earliest start time of the next gig at the same venue on the same day
    SELECT MIN(ag.ontime)
    INTO earliest_start_time
    FROM gig g
    JOIN act_gig ag ON g.gigID = ag.gigID
    WHERE g.venueID = NEW.venueID
      AND g.gigdatetime::DATE = NEW.gigdatetime::DATE
      AND g.gigdatetime > NEW.gigdatetime;

    -- Check if the gap between the new gig and the next gig is less than 180 minutes
    IF earliest_start_time IS NOT NULL AND NEW.gigdatetime + INTERVAL '180 minutes' > earliest_start_time THEN
        RAISE EXCEPTION 'Venue must have at least a 180-minute gap between gigs on the same day';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_venue_gap
BEFORE INSERT OR UPDATE ON gig
FOR EACH ROW
EXECUTE FUNCTION check_gig_interval();

-- # Rule 7: Trigger to ensure breaks are within a certain time limit --
CREATE OR REPLACE FUNCTION check_break_interval() RETURNS TRIGGER AS $$
DECLARE
    previous_end_time TIMESTAMP;
    next_start_time TIMESTAMP;
BEGIN 
    -- Find the latest act that ends before the new act starts
    SELECT MAX(ontime + INTERVAL '1 minute' * duration) INTO previous_end_time
    FROM act_gig
    WHERE gigID = NEW.gigID
    AND DATE(ontime) = DATE(NEW.ontime)
    AND ontime < NEW.ontime;

    -- Find the earliest act that starts after the new act ends
    SELECT MIN(ontime) INTO next_start_time
    FROM act_gig
    WHERE gigID = NEW.gigID
    AND DATE(ontime) = DATE(NEW.ontime)
    AND ontime > (NEW.ontime + INTERVAL '1 minute' * NEW.duration);

    -- Check break interval with the previous act
    IF previous_end_time IS NOT NULL THEN
        IF NEW.ontime - previous_end_time < INTERVAL '10 minute'
           OR NEW.ontime - previous_end_time > INTERVAL '30 minute' THEN
            RAISE EXCEPTION 'The break before this act must be between 10-30 minutes';
        END IF;
    END IF;

    -- Check break interval with the next act
    IF next_start_time IS NOT NULL THEN
        IF next_start_time - (NEW.ontime + INTERVAL '1 minute' * NEW.duration) < INTERVAL '10 minute'
           OR next_start_time - (NEW.ontime + INTERVAL '1 minute' * NEW.duration) > INTERVAL '30 minute' THEN
            RAISE EXCEPTION 'The break after this act must be between 10-30 minutes';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_break_interval
BEFORE INSERT OR UPDATE ON act_gig
FOR EACH ROW
EXECUTE FUNCTION check_break_interval();
 
-- # Rule 8: Trigger to ensure first act starts at the same time as gigdatetime
CREATE OR REPLACE FUNCTION check_first_act() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.ontime = (SELECT MIN(ontime) FROM act_gig WHERE gigID = NEW.gigID) THEN
        -- If this is the first act, check if it starts at the same time as gigdatetime
        IF NEW.ontime != (SELECT gigdatetime FROM gig WHERE gigID = NEW.gigID) THEN
            RAISE EXCEPTION 'First act must start at the same time as gigdatetime';
        END IF;
    END IF;

    -- Ensure that no act starts before the gig's start time (gigdatetime)
    IF NEW.ontime < (SELECT gigdatetime FROM gig WHERE gigID = NEW.gigID) THEN
        RAISE EXCEPTION 'An act cannot start before the gig start time';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_first_act
BEFORE INSERT ON act_gig
FOR EACH ROW
EXECUTE FUNCTION check_first_act();

-- # Rule 9: Trigger to ensure the number of tickets sold does not exceed the venue capacity
CREATE OR REPLACE FUNCTION check_ticket_sales() RETURNS TRIGGER AS $$
BEGIN
    -- Check the number of tickets already sold for the gig
    IF (SELECT COUNT(*) FROM ticket WHERE gigID = NEW.gigID) + 1 >
       (SELECT capacity FROM venue WHERE venueID = (SELECT venueID FROM gig WHERE gigID = NEW.gigID)) THEN
        RAISE EXCEPTION 'Cannot sell more tickets than the venue capacity for gigID %', NEW.gigID;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_ticket_sales
BEFORE INSERT ON ticket
FOR EACH ROW
EXECUTE FUNCTION check_ticket_sales();
 
-- # Rule 10: Trigger function to check if the final act finishes at least 60 minutes after the gig starts
CREATE OR REPLACE FUNCTION check_final_act_finish() RETURNS TRIGGER AS $$
BEGIN
    -- Find the latest ontime for the gig (final act)
    IF EXISTS (
        SELECT 1
        FROM act_gig ag
        JOIN gig g ON ag.gigID = g.gigID
        WHERE ag.gigID = NEW.gigID
        AND DATE(NEW.ontime) = DATE(ontime) 
        AND NEW.ontime + NEW.duration * INTERVAL '1 minute' < g.gigdatetime + INTERVAL '1 hour' -- 60 minutes after gig start
        AND ag.ontime = (SELECT MAX(ontime) FROM act_gig WHERE gigID = NEW.gigID) -- Last act
    ) THEN
        RAISE EXCEPTION 'The final act must finish at least 60 minutes after the gig starts';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_final_act_finish
BEFORE INSERT OR UPDATE ON act_gig
FOR EACH ROW
EXECUTE FUNCTION check_final_act_finish();

-- # Rule 11: Trigger function to check the end time for certain genres
CREATE OR REPLACE FUNCTION check_genre() RETURNS TRIGGER AS $$
DECLARE
    act_genre TEXT;
    act_end_time TIMESTAMP;
    next_day_one_am TIMESTAMP;
BEGIN
    -- Get the genre of the act
    SELECT a.genre INTO act_genre FROM act a WHERE a.actID = NEW.actID;
    
    -- Calculate the end time of the new act
    act_end_time := NEW.ontime + (NEW.duration * INTERVAL '1 minute');
    
    -- Calculate 1 am of the next day relative to NEW.ontime
    next_day_one_am := (NEW.ontime::DATE + INTERVAL '1 day' + INTERVAL '01:00:00');

    -- If the ontime occurs after or at midnight (between midnight and 1 am), we still want to check against the same day.
    IF NEW.ontime::TIME <= '01:00:00' THEN
        next_day_one_am := (NEW.ontime::DATE + INTERVAL '01:00:00');
    END IF;

    -- Display notices for debugging purposes
    RAISE NOTICE 'Checking genre for actID %: %', NEW.actID, act_genre;
    RAISE NOTICE 'Act duration: %', act_end_time;
    RAISE NOTICE '1 am: %', next_day_one_am;

    -- Check if the genre is Rock or Pop and ensure the finish time is before 11 pm
    IF act_genre IN ('Rock', 'Pop') AND act_end_time > (NEW.ontime::DATE + INTERVAL '23:00:00') THEN
        RAISE EXCEPTION 'Rock/Pop gigs must finish by 11pm';

    -- Check if the genre is not Rock or Pop and ensure the finish time is before 1 am
    ELSIF act_genre NOT IN ('Rock', 'Pop') AND act_end_time > next_day_one_am THEN
        RAISE EXCEPTION 'Non-Rock/Pop gigs must finish by 1am';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_genre_timings
BEFORE INSERT OR UPDATE ON act_gig
FOR EACH ROW
EXECUTE FUNCTION check_genre();


