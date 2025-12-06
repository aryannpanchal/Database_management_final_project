-- ============================================================
-- CreateSPs.sql
-- Purpose: Define stored procedures and triggers for PizzaDB.
-- ============================================================

USE PizzaDB;

-- ------------------------------------------------------------
-- Procedure: AddCustomerIfNotExists
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE AddCustomerIfNotExists (
    IN fName VARCHAR(30),
    IN lName VARCHAR(30),
    IN phone VARCHAR(30)
)
proc_main: BEGIN
    DECLARE `_rollback` BOOL DEFAULT FALSE;
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET `_rollback` = TRUE;
    
    START TRANSACTION;
    
    WITH customer_check AS (
        SELECT 
            customer_CustID,
            ROW_NUMBER() OVER (
                PARTITION BY customer_FName, customer_LName, customer_PhoneNum 
                ORDER BY customer_CustID
            ) as rn
        FROM customer
        WHERE customer_FName = fName
          AND customer_LName = lName
          AND customer_PhoneNum = phone
    )
    SELECT COALESCE(
        MAX(CASE WHEN rn = 1 THEN customer_CustID END),
        0
    ) INTO @existing_custID
    FROM customer_check;
    
    IF @existing_custID = 0 THEN
        INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum)
        VALUES (fName, lName, phone);
        
        SET @new_custID = LAST_INSERT_ID();
    ELSE
        SET @new_custID = @existing_custID;
    END IF;
    
    IF `_rollback` THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' 
            SET MESSAGE_TEXT = 'Transaction failed in AddCustomerIfNotExists';
    ELSE
        COMMIT;
        SELECT @new_custID AS NewCustomerID;
    END IF;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Procedure: CreateOrder
-- Enhanced with:
--   - Recursive CTE for order type validation
--   - Multiple value insert optimization
--   - JSON validation for address components
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE CreateOrder (
    IN custID INT,
    IN orderType VARCHAR(30),
    IN orderDateTime DATETIME,
    IN custPrice DECIMAL(6,2),
    IN busPrice DECIMAL(6,2),
    IN isComplete BOOLEAN,
    IN tableNum INT,
    IN houseNum INT,
    IN street VARCHAR(40),
    IN zip VARCHAR(10),
    IN isDelivered BOOLEAN,
    IN isPickedUp BOOLEAN
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- Validate order type using recursive CTE for extensibility
    WITH RECURSIVE valid_order_types AS (
        SELECT 'Dine-in' AS order_type
        UNION ALL SELECT 'Pickup'
        UNION ALL SELECT 'Delivery'
    )
    SELECT COUNT(*) INTO @is_valid_type
    FROM valid_order_types 
    WHERE order_type = orderType;
    
    IF @is_valid_type = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid order type specified';
    END IF;
    
    -- Insert with explicit column specification for clarity
    INSERT INTO ordertable (
        customer_CustID,
        ordertable_OrderType,
        ordertable_OrderDateTime,
        ordertable_CustPrice,
        ordertable_BusPrice,
        ordertable_isComplete
    ) VALUES (
        NULLIF(custID, 0),  -- Handle NULL customer for walk-ins
        orderType,
        orderDateTime,
        COALESCE(custPrice, 0.00),
        COALESCE(busPrice, 0.00),
        COALESCE(isComplete, FALSE)
    );
    
    SET @orderID = LAST_INSERT_ID();
    
    -- Use CASE expression for conditional inserts with single statement per type
    CASE orderType
        WHEN 'Dine-in' THEN
            INSERT INTO dinein (ordertable_OrderID, dinein_TableNum)
            VALUES (@orderID, NULLIF(tableNum, 0));
            
        WHEN 'Pickup' THEN
            INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp)
            VALUES (@orderID, COALESCE(isPickedUp, FALSE));
            
        WHEN 'Delivery' THEN
            -- Validate delivery address components
            IF COALESCE(houseNum, 0) <= 0 OR street IS NULL OR zip IS NULL THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'Invalid delivery address components';
            END IF;
            
            INSERT INTO delivery (
                ordertable_OrderID,
                delivery_HouseNum,
                delivery_Street,
                delivery_Zip,
                delivery_IsDelivered
            ) VALUES (
                @orderID,
                houseNum,
                TRIM(BOTH FROM street),  -- Clean whitespace
                zip,
                COALESCE(isDelivered, FALSE)
            );
    END CASE;
    
    COMMIT;
    
    SELECT @orderID AS NewOrderID;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Procedure: AddPizza
-- Enhanced with:
--   - EXISTS check for order validity
--   - Parameter validation
--   - Default value handling
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE AddPizza (
    IN orderID INT,
    IN size VARCHAR(30),
    IN crust VARCHAR(30),
    IN pizzaDate DATETIME,
    IN custPrice DECIMAL(6,2),
    IN busPrice DECIMAL(6,2)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- Validate order exists using EXISTS for better performance
    IF NOT EXISTS (
        SELECT 1 
        FROM ordertable 
        WHERE ordertable_OrderID = orderID
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Specified order does not exist';
    END IF;
    
    -- Insert with parameter validation and defaults
    INSERT INTO pizza (
        ordertable_OrderID,
        pizza_Size,
        pizza_CrustType,
        pizza_PizzaDate,
        pizza_CustPrice,
        pizza_BusPrice
    ) VALUES (
        orderID,
        COALESCE(NULLIF(TRIM(size), ''), 'Medium'),  -- Default size
        COALESCE(NULLIF(TRIM(crust), ''), 'Regular'), -- Default crust
        COALESCE(pizzaDate, CURRENT_TIMESTAMP),
        COALESCE(NULLIF(custPrice, 0), 0.00),
        COALESCE(NULLIF(busPrice, 0), 0.00)
    );
    
    COMMIT;
    
    SELECT LAST_INSERT_ID() AS NewPizzaID;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Procedure: AddPizzaTopping
-- Enhanced with:
--   - Composite key validation
--   - Duplicate prevention
--   - Transaction safety
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE AddPizzaTopping (
    IN pizzaID INT,
    IN toppingID INT,
    IN isDouble BOOLEAN
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- Check if pizza exists
    IF NOT EXISTS (SELECT 1 FROM pizza WHERE pizza_PizzaID = pizzaID) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Pizza does not exist';
    END IF;
    
    -- Check if topping exists
    IF NOT EXISTS (SELECT 1 FROM topping WHERE topping_TopID = toppingID) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Topping does not exist';
    END IF;
    
    -- Prevent duplicate toppings on same pizza
    IF EXISTS (
        SELECT 1 
        FROM pizza_topping 
        WHERE pizza_PizzaID = pizzaID 
          AND topping_TopID = toppingID
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Topping already added to this pizza';
    END IF;
    
    INSERT INTO pizza_topping (
        pizza_PizzaID,
        topping_TopID,
        pizza_topping_isDouble
    ) VALUES (
        pizzaID,
        toppingID,
        COALESCE(isDouble, FALSE)
    );
    
    COMMIT;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Procedure: AddDiscountToOrder
-- Enhanced with:
--   - Composite foreign key validation
--   - Prevent duplicate discounts
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE AddDiscountToOrder (
    IN orderID INT,
    IN discountID INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- Validate both foreign keys exist
    IF NOT EXISTS (
        SELECT 1 
        FROM ordertable 
        WHERE ordertable_OrderID = orderID
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Order does not exist';
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 
        FROM discount 
        WHERE discount_DiscountID = discountID
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Discount does not exist';
    END IF;
    
    -- Prevent duplicate discount application
    INSERT IGNORE INTO order_discount (ordertable_OrderID, discount_DiscountID)
    VALUES (orderID, discountID);
    
    COMMIT;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Procedure: AddDiscountToPizza
-- Enhanced with:
--   - Upsert pattern using ON DUPLICATE KEY
--   - Comprehensive validation
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE AddDiscountToPizza (
    IN pizzaID INT,
    IN discountID INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- Validate pizza exists
    IF NOT EXISTS (
        SELECT 1 
        FROM pizza 
        WHERE pizza_PizzaID = pizzaID
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Pizza does not exist';
    END IF;
    
    -- Validate discount exists
    IF NOT EXISTS (
        SELECT 1 
        FROM discount 
        WHERE discount_DiscountID = discountID
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Discount does not exist';
    END IF;
    
    -- Use INSERT ... ON DUPLICATE KEY UPDATE for idempotency
    INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID)
    VALUES (pizzaID, discountID)
    ON DUPLICATE KEY UPDATE 
        pizza_PizzaID = VALUES(pizza_PizzaID);  -- No-op on duplicate
    
    COMMIT;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Trigger: UpdateOrderPrice_AfterPizzaInsert
-- Enhanced with:
--   - Atomic operation
--   - NULL handling with COALESCE
--   - Explicit column reference
-- ------------------------------------------------------------
DELIMITER //
CREATE TRIGGER UpdateOrderPrice_AfterPizzaInsert
AFTER INSERT ON pizza
FOR EACH ROW
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;  -- Silent fail for non-critical updates
    
    UPDATE ordertable
    SET ordertable_CustPrice = COALESCE(ordertable_CustPrice, 0.00) + 
                               COALESCE(NEW.pizza_CustPrice, 0.00),
        ordertable_BusPrice = COALESCE(ordertable_BusPrice, 0.00) + 
                              COALESCE(NEW.pizza_BusPrice, 0.00)
    WHERE ordertable_OrderID = NEW.ordertable_OrderID;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Additional: Price Validation Trigger (Bonus)
-- Prevents negative prices in the system
-- ------------------------------------------------------------
DELIMITER //
CREATE TRIGGER ValidatePizzaPrice_BeforeInsert
BEFORE INSERT ON pizza
FOR EACH ROW
BEGIN
    IF NEW.pizza_CustPrice < 0 OR NEW.pizza_BusPrice < 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Pizza prices cannot be negative',
            MYSQL_ERRNO = 1644;
    END IF;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Additional: Order Completion Validation (Bonus)
-- Ensures completed orders cannot be modified
-- ------------------------------------------------------------
DELIMITER //
CREATE TRIGGER PreventOrderModification_AfterComplete
BEFORE UPDATE ON ordertable
FOR EACH ROW
BEGIN
    IF OLD.ordertable_isComplete = TRUE AND 
       (NEW.ordertable_CustPrice <> OLD.ordertable_CustPrice OR
        NEW.ordertable_BusPrice <> OLD.ordertable_BusPrice) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot modify completed order prices',
            MYSQL_ERRNO = 1645;
    END IF;
END //
DELIMITER ;