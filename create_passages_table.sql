-- Create passages table for Textverständnis passages
CREATE TABLE `passages` (
  `id` int NOT NULL AUTO_INCREMENT,
  `subcategory_id` int NOT NULL,
  `test_simulation_id` int DEFAULT NULL,
  `text` text NOT NULL,
  `source` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_passages_subcategory_id` (`subcategory_id`),
  KEY `idx_passages_test_simulation_id` (`test_simulation_id`),
  CONSTRAINT `fk_passages_subcategory` FOREIGN KEY (`subcategory_id`) REFERENCES `subcategories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_passages_test_simulation` FOREIGN KEY (`test_simulation_id`) REFERENCES `test_simulations` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
