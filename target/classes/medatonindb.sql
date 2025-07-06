INSERT INTO Subcategories (category_id, name, order_index) VALUES
((SELECT id FROM Categories WHERE name='Biologie'), 'Sub 1', 1),
((SELECT id FROM Categories WHERE name='Biologie'), 'Sub 2', 2),
((SELECT id FROM Categories WHERE name='Biologie'), 'Sub 3', 3),
((SELECT id FROM Categories WHERE name='Biologie'), 'Sub 4', 4);
