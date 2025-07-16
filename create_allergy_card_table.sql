-- Create allergy_card table for storing Merkfähigkeiten test data
CREATE TABLE `allergy_card` (
  `id` int NOT NULL AUTO_INCREMENT,
  `test_session_id` int NOT NULL,
  `idx` int NOT NULL,
  `name` varchar(100) NOT NULL,
  `dob` date DEFAULT NULL,
  `medication` varchar(10) DEFAULT NULL,
  `blood_group` varchar(5) DEFAULT NULL,
  `allergies` text DEFAULT NULL,
  `card_no` varchar(20) DEFAULT NULL,
  `country` varchar(50) DEFAULT NULL,
  `image_png` longblob DEFAULT NULL,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_test_session_id` (`test_session_id`),
  CONSTRAINT `fk_allergy_card_session` FOREIGN KEY (`test_session_id`) REFERENCES `test_simulations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
