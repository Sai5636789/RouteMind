/* Sample demo dataset: the fleet and orders the demo walkthrough starts
   from, so a fresh database is useful the moment the stack is up.

   - 8 vehicles (VAN-001 to VAN-008) at the Gainesville depot, capacities
     100 to 300 kg, all AVAILABLE.
   - 8 active drivers.
   - 24 unassigned CREATED orders at real Gainesville locations (campus,
     Butler Plaza, Archer Road, the malls, the clinics), weights 11 to
     40 kg, 550 kg in total — more than any one vehicle carries, so the
     optimizer has to split the work across the fleet.

   The depot id is looked up, not hard-coded, because V1 generates it.
   Nothing here is assigned: the dispatcher still creates the plan,
   approves it, and works the deliveries, which is the point of the
   demo. To start over, drop the database volume (`docker compose
   down -v`) and start again; Flyway applies V1 to V5 on first boot. */

INSERT INTO vehicles (plate_number, model, capacity_kg, status, depot_id) VALUES
    ('VAN-001', 'Ford Transit', 300, 'AVAILABLE', (SELECT id FROM depots LIMIT 1)),
    ('VAN-002', 'Mercedes Sprinter', 250, 'AVAILABLE', (SELECT id FROM depots LIMIT 1)),
    ('VAN-003', 'Ram ProMaster', 200, 'AVAILABLE', (SELECT id FROM depots LIMIT 1)),
    ('VAN-004', 'Ford Transit', 200, 'AVAILABLE', (SELECT id FROM depots LIMIT 1)),
    ('VAN-005', 'Mercedes Sprinter', 150, 'AVAILABLE', (SELECT id FROM depots LIMIT 1)),
    ('VAN-006', 'Ram ProMaster', 150, 'AVAILABLE', (SELECT id FROM depots LIMIT 1)),
    ('VAN-007', 'Ford Transit', 100, 'AVAILABLE', (SELECT id FROM depots LIMIT 1)),
    ('VAN-008', 'Nissan NV200', 100, 'AVAILABLE', (SELECT id FROM depots LIMIT 1));

INSERT INTO drivers (first_name, last_name, license_number, phone, status) VALUES
    ('Ana', 'Reyes', 'FL-DRV-001', '(352) 555-0101', 'ACTIVE'),
    ('Ben', 'Ortiz', 'FL-DRV-002', '(352) 555-0102', 'ACTIVE'),
    ('Carla', 'Mendes', 'FL-DRV-003', '(352) 555-0103', 'ACTIVE'),
    ('David', 'Kim', 'FL-DRV-004', '(352) 555-0104', 'ACTIVE'),
    ('Elena', 'Torres', 'FL-DRV-005', '(352) 555-0105', 'ACTIVE'),
    ('Frank', 'Doyle', 'FL-DRV-006', '(352) 555-0106', 'ACTIVE'),
    ('Grace', 'Liu', 'FL-DRV-007', '(352) 555-0107', 'ACTIVE'),
    ('Henry', 'Patel', 'FL-DRV-008', '(352) 555-0108', 'ACTIVE');

INSERT INTO orders (customer_name, delivery_address, latitude, longitude, weight_kg, status) VALUES
    ('UF Campus Store', '1 Stadium Rd, Gainesville, FL', 29.6465, -82.3488, 30, 'CREATED'),
    ('Butler Plaza Pharmacy', '4200 SW 33rd Ave, Gainesville, FL', 29.6230, -82.3830, 25, 'CREATED'),
    ('Archer Road Pharmacy', '3500 SW Archer Rd, Gainesville, FL', 29.6320, -82.3680, 20, 'CREATED'),
    ('Eastside Clinic', '900 NE 15th St, Gainesville, FL', 29.6680, -82.3050, 35, 'CREATED'),
    ('NW Gainesville Clinic', '4500 NW 39th Ave, Gainesville, FL', 29.6900, -82.3800, 15, 'CREATED'),
    ('Shands Hospital Pharmacy', '1600 SW Archer Rd, Gainesville, FL', 29.6400, -82.3430, 40, 'CREATED'),
    ('Oaks Mall Pharmacy', '6419 W Newberry Rd, Gainesville, FL', 29.6590, -82.4040, 18, 'CREATED'),
    ('Haile Plantation Clinic', '5230 SW 91st Ter, Gainesville, FL', 29.6180, -82.3980, 22, 'CREATED'),
    ('Downtown Pharmacy', '100 SE 2nd Ave, Gainesville, FL', 29.6510, -82.3240, 12, 'CREATED'),
    ('UF Health Springhill', '4037 NW 86th Ter, Gainesville, FL', 29.6850, -82.3950, 28, 'CREATED'),
    ('Tioga Town Center Pharmacy', '13005 SW 1st Rd, Gainesville, FL', 29.6180, -82.4150, 16, 'CREATED'),
    ('Celebration Pointe Pharmacy', '5001 Celebration Pointe Ave, Gainesville, FL', 29.6250, -82.3900, 24, 'CREATED'),
    ('Northwood Pharmacy', '2400 NW 43rd St, Gainesville, FL', 29.6680, -82.3600, 14, 'CREATED'),
    ('Millhopper Clinic', '2840 NW 43rd St, Gainesville, FL', 29.6820, -82.3650, 26, 'CREATED'),
    ('Jonesville Pharmacy', '13820 W Newberry Rd, Gainesville, FL', 29.6400, -82.4300, 19, 'CREATED'),
    ('Newberry Clinic', '25020 W Newberry Rd, Gainesville, FL', 29.6400, -82.4400, 32, 'CREATED'),
    ('Waldo Road Clinic', '2900 NE Waldo Rd, Gainesville, FL', 29.6700, -82.3000, 17, 'CREATED'),
    ('Tower Road Pharmacy', '5200 SW 20th Ave, Gainesville, FL', 29.6300, -82.3750, 21, 'CREATED'),
    ('University Ave Pharmacy', '1200 W University Ave, Gainesville, FL', 29.6520, -82.3350, 11, 'CREATED'),
    ('South Main Clinic', '800 S Main St, Gainesville, FL', 29.6400, -82.3260, 23, 'CREATED'),
    ('Gainesville Health Center', '1700 SW 16th Ave, Gainesville, FL', 29.6380, -82.3400, 27, 'CREATED'),
    ('Duckpond Pharmacy', '700 NE 8th Ave, Gainesville, FL', 29.6600, -82.3150, 13, 'CREATED'),
    ('Hogtown Creek Clinic', '2300 NW 6th St, Gainesville, FL', 29.6750, -82.3350, 33, 'CREATED'),
    ('Paynes Prairie Pharmacy', '10000 SW Williston Rd, Gainesville, FL', 29.6100, -82.3600, 29, 'CREATED');
