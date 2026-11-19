INSERT INTO categories (code, name, description)
VALUES
    ('NHANG-TRAM', 'Nhang Tram Huong', 'Nhang tram huong tu nhien khong hoa chat, huong thom thanh khiet'),
    ('TRAM-NU', 'Tram Nu Thac Khoi', 'Tram nu nguyen chat dung cho thac khoi va thuong tram'),
    ('PHU-KIEN', 'Phu Kien Xong Tram', 'Lu dong, thac khoi gom su va dung cu thuong tram')
ON CONFLICT (code) DO NOTHING;

INSERT INTO products (sku, name, description, unit_price, stock_quantity, category_id, version)
VALUES
    ('MH-NHANG-01', 'Nhang Tram Khong Tam Cao Cap', 'Hop 100 que nhang tram huong Quang Nam nguyen chat', 350000.00, 50,
     (SELECT id FROM categories WHERE code = 'NHANG-TRAM'), 0),
    ('MH-NHANG-02', 'Nhang Tram Khoanh Tu Nhien', 'Hop 24 khoanh tram huong thoi gian chay 4 gio moi khoanh', 280000.00, 40,
     (SELECT id FROM categories WHERE code = 'NHANG-TRAM'), 0),
    ('MH-NU-01', 'Tram Nu Que Lam Hop 40 Vien', 'Tram nu ket hop bot tram va vo que tu nhien am ap', 220000.00, 60,
     (SELECT id FROM categories WHERE code = 'TRAM-NU'), 0),
    ('MH-NU-02', 'Tram Nu Ky Nam Thuong Hang', 'Tram nu lau nam huong ngot sau danh cho phong thien', 650000.00, 20,
     (SELECT id FROM categories WHERE code = 'TRAM-NU'), 0),
    ('MH-LU-01', 'Lu Xong Tram Dong Co Dien', 'Lu dong duc thu cong hoa van sen co dien cao 12cm', 890000.00, 15,
     (SELECT id FROM categories WHERE code = 'PHU-KIEN'), 0),
    ('MH-THAC-01', 'Thac Khoi Tram Huong Gom Su', 'Thac khoi nguoc gom men ngoc hoa tiet nui non tinh xao', 540000.00, 25,
     (SELECT id FROM categories WHERE code = 'PHU-KIEN'), 0)
ON CONFLICT (sku) DO NOTHING;
