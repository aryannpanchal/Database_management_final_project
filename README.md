# 🍕 Pizzas-R-Us Order Management System

A console-based order management system for a fictional pizza restaurant, built with **Java** and **MySQL**.

This project demonstrates:

- Relational database design (ERD → schema → SQL scripts)
- Java–MySQL integration via **JDBC**
- A layered architecture with:
  - A console UI (`Menu`)
  - A data/business layer (`DBNinja`)
  - Domain model classes (`Order`, `Pizza`, `Topping`, etc.)
- SQL views for simple business analytics (topping popularity & profit reports)

---

## 📚 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Database Schema](#-database-schema)
  - [Core Tables](#core-tables)
  - [Analytics Views](#analytics-views)
- [Project Structure](#-project-structure)
- [Setup & Installation](#-setup--installation)
  - [1. Prerequisites](#1-prerequisites)
  - [2. Configure MySQL](#2-configure-mysql)
  - [3. Create & Populate the Database](#3-create--populate-the-database)
  - [4. Configure the Java Connection](#4-configure-the-java-connection)
  - [5. Compile & Run](#5-compile--run)
- [Using the Application](#-using-the-application)
  - [Menu Options](#menu-options)
  - [Typical Workflows](#typical-workflows)
- [Business Logic Details](#-business-logic-details)
  - [Order Types](#order-types)
  - [Pizza Pricing](#pizza-pricing)
  - [Discounts](#discounts)
  - [Inventory](#inventory)
- [Known Assumptions & Notes](#-known-assumptions--notes)
- [Extending the Project](#-extending-the-project)

---

## 🧠 Overview

**Pizzas-R-Us** is a small simulation of a pizza shop’s back-of-house system. It lets a user:

- Create and manage **dine-in**, **pickup**, and **delivery** orders
- Add pizzas, toppings, and discounts to those orders
- Track basic topping inventory
- Generate simple profitability reports

The project is designed as a course assignment to practice:

- Designing and implementing a relational schema in MySQL
- Accessing the database through Java and JDBC
- Keeping concerns separated between UI, data access, and business rules

---

## ✅ Features

### Order Management

- Create:
  - **Dine-in** orders (with table numbers)
  - **Pickup** orders (linked to customers)
  - **Delivery** orders (linked to customers and addresses)
- Add multiple pizzas to a single order
- Configure each pizza:
  - Size (`Small`, `Medium`, `Large`, `XLarge`)
  - Crust type (`Thin`, `Original`, `Pan`, `Gluten-Free`)
  - Toppings (with optional “extra”/double portion)
  - Pizza-level discounts
- Apply **order-level discounts**
- Mark orders as:
  - Prepared / complete
  - Picked up
  - Delivered

### Customer Management

- View the list of customers
- Add new customers
- Find customers by phone (used internally by DBNinja)
- Link pickup/delivery orders to existing or newly created customers

### Inventory Management

- View current topping inventory levels
- Add units to topping inventory

### Reporting (via SQL Views)

- **ToppingPopularity**  
  Shows how often each topping is used (extra counts as double).
- **ProfitByPizza**  
  Shows profit by pizza size & crust type per month.
- **ProfitByOrderType**  
  Shows profit by order type (`dinein`, `delivery`, `pickup`) plus a **grand total**.

---

## 🧰 Tech Stack

- **Language:** Java (JDK 17+ or compatible)
- **Database:** MySQL 8+ (or compatible)
- **Connectivity:** JDBC (MySQL Connector/J, e.g. `com.mysql.cj.jdbc.Driver`)
- **Execution:** `javac` / `java` from the command line or within an IDE

---

## 🏗 Architecture

**High-level layers:**

1. **Console UI**
   - `Menu.java`
   - Handles all user interaction (input/output)
   - Presents the menu, reads user input, and calls `DBNinja` for actual work

2. **Data Access & Business Logic**
   - `DBNinja.java`
   - Knows how to:
     - Open/close database connections (`DBConnector`)
     - Insert and fetch orders, pizzas, toppings, discounts, customers
     - Execute views and transform results into domain objects
     - Apply some pricing & mapping rules

3. **Domain Model (Plain Java Objects / POJOs)**
   - `Customer`
   - `Order` (base class)
     - `DineinOrder`
     - `PickupOrder`
     - `DeliveryOrder`
   - `Pizza`
   - `Topping`
   - `Discount`

4. **Database Scripts**
   - `CreateTables.sql` – schema & tables
   - `PopulateData.sql` – seed data (customers, toppings, orders, pizzas, discounts)
   - `CreateViews.sql` – analytic views
   - `DropTables.sql` – clean drop of views and tables

---

## 🗄 Database Schema

### Core Tables

#### 1. `customer`

Stores customer information for pickup/delivery orders.

| Column              | Type         | Description                 |
|---------------------|-------------|-----------------------------|
| `customer_CustID`   | INT (PK)    | Auto-increment ID           |
| `customer_FName`    | VARCHAR(30) | First name                  |
| `customer_LName`    | VARCHAR(30) | Last name                   |
| `customer_PhoneNum` | VARCHAR(30) | Phone number (string)       |

---

#### 2. `baseprice`

Stores base pricing by size and crust type.

| Column                 | Type           | Description                       |
|------------------------|----------------|-----------------------------------|
| `baseprice_Size`       | VARCHAR(30)    | `Small`, `Medium`, `Large`, `XLarge` |
| `baseprice_CrustType`  | VARCHAR(30)    | `Thin`, `Original`, `Pan`, `Gluten-Free` |
| `baseprice_CustPrice`  | DECIMAL(10,2)  | Base price to customer           |
| `baseprice_BusPrice`   | DECIMAL(10,2)  | Base business cost               |

**Primary Key:** `(baseprice_Size, baseprice_CrustType)`

---

#### 3. `ordertable`

Represents a single order.

| Column                     | Type           | Description                                |
|----------------------------|----------------|--------------------------------------------|
| `ordertable_OrderID`       | INT (PK)       | Auto-increment order ID                    |
| `ordertable_OrderType`     | VARCHAR(30)    | `dinein`, `pickup`, or `delivery`          |
| `ordertable_OrderDateTime` | DATETIME       | Date and time of the order                 |
| `ordertable_CustPrice`     | DECIMAL(10,2)  | Total customer-facing price for the order  |
| `ordertable_BusPrice`      | DECIMAL(10,2)  | Total business cost for the order          |
| `ordertable_IsComplete`    | TINYINT(1)     | 0 = open, 1 = complete                     |
| `customer_CustID`          | INT (FK, NULL) | Linked customer for pickup/delivery orders |

Dine-in orders typically have `customer_CustID = NULL`.

---

#### 4. Order Type Tables

These tables specialize orders by type and are 1:1 with `ordertable` rows of that type.

- **`dinein`**

  | Column               | Type | Description               |
  |----------------------|------|---------------------------|
  | `ordertable_OrderID` | INT  | PK, FK → `ordertable`     |
  | `dinein_TableNum`    | INT  | Restaurant table number   |

- **`pickup`**

  | Column                 | Type        | Description                          |
  |------------------------|------------|--------------------------------------|
  | `ordertable_OrderID`   | INT        | PK, FK → `ordertable`                |
  | `pickup_IsPickedUp`    | TINYINT(1) | 0 = not picked up, 1 = picked up     |

- **`delivery`**

  | Column                 | Type        | Description                          |
  |------------------------|------------|--------------------------------------|
  | `ordertable_OrderID`   | INT        | PK, FK → `ordertable`                |
  | `delivery_HouseNum`    | INT        | House number                         |
  | `delivery_Street`      | VARCHAR(30)| Street                               |
  | `delivery_City`        | VARCHAR(30)| City                                 |
  | `delivery_State`       | VARCHAR(2) | State abbreviation                   |
  | `delivery_Zip`         | INT        | ZIP code                             |
  | `delivery_IsDelivered` | TINYINT(1) | 0 = not delivered, 1 = delivered     |

All have foreign keys back to `ordertable` with `ON DELETE CASCADE`.

---

#### 5. `topping`

Stores topping metadata + inventory.

| Column            | Type           | Description                            |
|-------------------|----------------|----------------------------------------|
| `topping_TopID`   | INT (PK)       | Auto-increment topping ID              |
| `topping_TopName` | VARCHAR(30)    | Topping name                           |
| `topping_SmallAMT`| DECIMAL(10,2)  | Units used on a *Small* pizza         |
| `topping_MedAMT`  | DECIMAL(10,2)  | Units used on a *Medium* pizza        |
| `topping_LgAMT`   | DECIMAL(10,2)  | Units used on a *Large* pizza         |
| `topping_XLAMT`   | DECIMAL(10,2)  | Units used on an *XLarge* pizza       |
| `topping_CustPrice`| DECIMAL(10,2) | Price per unit to customer            |
| `topping_BusPrice` | DECIMAL(10,2) | Cost per unit to the business         |
| `topping_MinINVT` | INT            | Minimum inventory threshold           |
| `topping_CurINVT` | INT            | Current inventory level               |

---

#### 6. `pizza`

Each row stores a single pizza in an order.

| Column             | Type           | Description                                |
|--------------------|----------------|--------------------------------------------|
| `pizza_PizzaID`    | INT (PK)       | Auto-increment pizza ID                    |
| `ordertable_OrderID` | INT (FK)    | Parent order ID                            |
| `pizza_Size`       | VARCHAR(30)    | `Small`, `Medium`, `Large`, `XLarge`      |
| `pizza_CrustType`  | VARCHAR(30)    | `Thin`, `Original`, `Pan`, `Gluten-Free`  |
| `pizza_PizzaState` | VARCHAR(20)    | e.g. `'completed'`                         |
| `pizza_PizzaDate`  | DATETIME       | Timestamp for the pizza row                |
| `pizza_CustPrice`  | DECIMAL(10,2)  | Final price charged to customer            |
| `pizza_BusPrice`   | DECIMAL(10,2)  | Final business cost                        |

---

#### 7. `pizza_topping`

Many-to-many relationship between pizzas and toppings.

| Column                   | Type | Description                                  |
|--------------------------|------|----------------------------------------------|
| `pizza_PizzaID`          | INT  | FK → `pizza`                                |
| `topping_TopID`          | INT  | FK → `topping`                              |
| `pizza_topping_IsDouble` | INT  | 0 = normal, 1 = “extra” / double portion    |

**Primary Key:** `(pizza_PizzaID, topping_TopID)`

---

#### 8. Discounts

- **`discount`**  
  Stores definitions of discounts:

  | Column               | Type           | Description                       |
  |----------------------|----------------|-----------------------------------|
  | `discount_DiscountID`| INT (PK)       | Auto-increment ID                 |
  | `discount_DiscountName` | VARCHAR(30) | Name of discount                  |
  | `discount_Amount`    | DECIMAL(10,2)  | Percentage or dollar amount       |
  | `discount_IsPercent` | TINYINT(1)     | 1 = percentage, 0 = flat amount   |

- **`pizza_discount`**  
  Many-to-many between pizzas and discounts.

- **`order_discount`**  
  Many-to-many between orders and discounts.

---

### Analytics Views

Defined in `CreateViews.sql`:

1. **`ToppingPopularity`**

   - Columns: `Topping`, `ToppingCount`
   - Counts how often each topping is used (double toppings counted as 2).

2. **`ProfitByPizza`**

   - Columns: `Size`, `Crust`, `Profit`, `OrderMonth`
   - Aggregates profit by pizza size + crust type per month.

3. **`ProfitByOrderType`**

   - Columns: `CustomerType`, `OrderMonth`, `TotalOrderPrice`, `TotalOrderCost`, `Profit`
   - `CustomerType` is empty string on the grand total row.
   - Includes per-type rows and one **Grand Total** row.

---

## 📁 Project Structure

Example layout:

```text
project-root/
├── src/
│   └── cpsc4620/
│       ├── DBConnector.java
│       ├── DBNinja.java
│       ├── Menu.java
│       ├── Customer.java
│       ├── Order.java
│       ├── DineinOrder.java
│       ├── PickupOrder.java
│       ├── DeliveryOrder.java
│       ├── Pizza.java
│       ├── Topping.java
│       └── Discount.java
├── sql/
│   ├── CreateTables.sql
│   ├── PopulateData.sql
│   ├── CreateViews.sql
│   └── DropTables.sql
└── README.md
