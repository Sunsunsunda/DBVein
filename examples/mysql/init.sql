-- Synthetic data only. customer_ref intentionally has no foreign key.
CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(80), status INT);
CREATE TABLE orders (id INT PRIMARY KEY, customer_ref INT, status INT, amount DECIMAL(12,2));
CREATE TABLE order_items (
  id INT PRIMARY KEY, order_id INT, sku VARCHAR(40),
  CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
INSERT INTO customers VALUES
(1,'Customer A',0),(2,'Customer B',1),(3,'Customer C',0),
(4,'Customer D',1),(5,'Customer E',0),(6,'Customer F',1);
INSERT INTO orders VALUES
(101,1,0,100),(102,2,1,200),(103,3,0,300),(104,4,1,400),
(105,5,0,500),(106,6,1,600),(107,1,1,150),(108,2,0,250);
INSERT INTO order_items VALUES
(1001,101,'A'),(1002,102,'B'),(1003,103,'C'),(1004,104,'D'),
(1005,105,'E'),(1006,106,'F'),(1007,107,'A'),(1008,108,'B');
CREATE USER 'dbvein_reader'@'%' IDENTIFIED BY 'demo-reader-local-only';
GRANT SELECT ON dbvein_demo.* TO 'dbvein_reader'@'%';
