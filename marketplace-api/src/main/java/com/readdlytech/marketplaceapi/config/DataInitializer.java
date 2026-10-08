package com.readdlytech.marketplaceapi.config;

import com.readdlytech.marketplaceapi.entity.*;
import com.readdlytech.marketplaceapi.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Profile("(dev | local) & !ha")
public class DataInitializer implements CommandLineRunner {
    private static final int TARGET_PART_COUNT = 60;
    private static final String SELLER_PASSWORD = "Seller123!";
    private static final String BUYER_PASSWORD = "Buyer123!";

    private static final String[] LOCATIONS = {
            "Tunis", "Sousse", "Sfax"
    };
    private static final String[] CONDITIONS = {"Like new", "Used - good", "Used - good", "Used - fair", "New"};
    private static final String[] IMAGE_URLS = {
            "https://images.unsplash.com/photo-1486262715619-67b85e0b08d3?auto=format&fit=crop&w=1000&q=80",
            "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=1000&q=80",
            "https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1000&q=80",
            "https://images.unsplash.com/photo-1542362567-b07e54358753?auto=format&fit=crop&w=1000&q=80",
            "https://images.unsplash.com/photo-1553440569-bcc63803a83d?auto=format&fit=crop&w=1000&q=80"
    };

    private static final List<CategorySeed> CATEGORY_SEEDS = List.of(
            new CategorySeed("Engine Parts"), new CategorySeed("Brake Systems"),
            new CategorySeed("Electrical Parts"), new CategorySeed("Suspension"),
            new CategorySeed("Body & Exterior"), new CategorySeed("Interior"),
            new CategorySeed("Transmission"), new CategorySeed("Cooling & Heating")
    );

    private static final List<VehicleSeed> VEHICLE_SEEDS = List.of(
            // Make IDs, display names, model names, and years follow CarQuery's getModels catalog shape.
            new VehicleSeed("Toyota", "Corolla", 2020, "1.8L", "Petrol"),
            new VehicleSeed("Toyota", "Yaris", 2019, "1.5L", "Petrol"),
            new VehicleSeed("Volkswagen", "Golf", 2020, "1.6 TDI", "Diesel"),
            new VehicleSeed("Volkswagen", "Passat", 2019, "2.0 TDI", "Diesel"),
            new VehicleSeed("Renault", "Clio", 2020, "1.0 TCe", "Petrol"),
            new VehicleSeed("Renault", "Megane", 2019, "1.5 dCi", "Diesel"),
            new VehicleSeed("Peugeot", "208", 2020, "1.2 PureTech", "Petrol"),
            new VehicleSeed("Peugeot", "308", 2019, "1.5 BlueHDi", "Diesel"),
            new VehicleSeed("Citroën", "C3", 2020, "1.2 PureTech", "Petrol"),
            new VehicleSeed("Citroën", "C4 Cactus", 2019, "1.6 BlueHDi", "Diesel"),
            new VehicleSeed("BMW", "3 Series", 2020, "2.0L", "Diesel"),
            new VehicleSeed("BMW", "5 Series", 2019, "2.0L", "Diesel"),
            new VehicleSeed("Mercedes-Benz", "C-Class", 2020, "2.0L", "Petrol"),
            new VehicleSeed("Mercedes-Benz", "E-Class", 2019, "2.0L", "Diesel"),
            new VehicleSeed("Ford", "Focus", 2020, "1.5 EcoBlue", "Diesel"),
            new VehicleSeed("Ford", "Fiesta", 2019, "1.0 EcoBoost", "Petrol"),
            new VehicleSeed("Hyundai", "i20", 2020, "1.2L", "Petrol"),
            new VehicleSeed("Hyundai", "Tucson", 2019, "1.6 CRDi", "Diesel"),
            new VehicleSeed("Kia", "Rio", 2020, "1.4L", "Petrol"),
            new VehicleSeed("Kia", "Sportage", 2019, "1.6 CRDi", "Diesel")
    );

    private static final List<PartSeed> PART_SEEDS = List.of(
            new PartSeed("Complete engine assembly", "Engine Parts", 4200),
            new PartSeed("Turbocharger unit", "Engine Parts", 1250),
            new PartSeed("Alternator", "Electrical Parts", 420),
            new PartSeed("Starter motor", "Electrical Parts", 350),
            new PartSeed("LED front headlight", "Electrical Parts", 680),
            new PartSeed("Rear tail light", "Electrical Parts", 260),
            new PartSeed("Front brake caliper", "Brake Systems", 310),
            new PartSeed("Brake disc pair", "Brake Systems", 280),
            new PartSeed("ABS pump module", "Brake Systems", 760),
            new PartSeed("Front shock absorber", "Suspension", 290),
            new PartSeed("Steering rack", "Suspension", 890),
            new PartSeed("Automatic gearbox", "Transmission", 3600),
            new PartSeed("Clutch kit", "Transmission", 720),
            new PartSeed("Radiator", "Cooling & Heating", 390),
            new PartSeed("Air conditioning compressor", "Cooling & Heating", 850),
            new PartSeed("Front bumper", "Body & Exterior", 540),
            new PartSeed("Left door mirror", "Body & Exterior", 240),
            new PartSeed("Driver seat", "Interior", 480),
            new PartSeed("Dashboard instrument cluster", "Interior", 620),
            new PartSeed("Multimedia head unit", "Interior", 750)
    );

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final VehicleRepository vehicleRepository;
    private final PartRepository partRepository;
    private final ImageRepository imageRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        migratePreviousBrandAccounts();
        createAdmin();
        List<User> buyers = createBuyers();
        Map<String, Category> categories = createCategories();
        List<Vehicle> vehicles = createVehicles();
        List<User> sellers = createSellers();
        int added = createParts(categories, vehicles, sellers);
        int corrected = normalizeSeededPartPositions(sellers);
        System.out.printf("E-biessa development data ready: %d sellers, %d buyers, %d vehicles, %d total parts (%d added).%n",
                sellers.size(), buyers.size(), vehicles.size(), partRepository.count(), added);
        if (corrected > 0) System.out.printf("Corrected position metadata on %d seeded listings.%n", corrected);
    }

    private void migratePreviousBrandAccounts() {
        migrateEmail("admin@partly.tn", "admin@e-biessa.tn", "E-biessa Administrator", null);
        migrateEmail("admin@e-bieca.tn", "admin@e-biessa.tn", "E-biessa Administrator", null);
        migrateEmail("seller@partly.tn", "seller@e-biessa.tn", null, "E-biessa Test Garage");
        migrateEmail("seller@e-bieca.tn", "seller@e-biessa.tn", null, "E-biessa Test Garage");
    }

    private void migrateEmail(String oldEmail, String newEmail, String fullName, String businessName) {
        userRepository.findByEmail(oldEmail).ifPresent(previous -> {
            if (!userRepository.existsByEmail(newEmail)) {
                previous.setEmail(newEmail);
                if (fullName != null) previous.setFullName(fullName);
                if (businessName != null) previous.setBusinessName(businessName);
                userRepository.save(previous);
            }
        });
    }

    private void createAdmin() {
        if (userRepository.existsByEmail("admin@e-biessa.tn")) return;
        User admin = new User();
        admin.setEmail("admin@e-biessa.tn");
        admin.setPassword(passwordEncoder.encode("Admin123!"));
        admin.setFullName("E-biessa Administrator");
        admin.setLocation("Tunis, Tunisia");
        admin.setBio("Marketplace operations, catalog quality, and community safety.");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
    }

    private List<User> createBuyers() {
        String[] names = {"Leila Ben Ali", "Omar Trabelsi", "Nadia Gharbi"};
        List<User> buyers = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            String email = String.format(Locale.ROOT, "buyer%02d@e-biessa.tn", i + 1);
            User buyer = userRepository.findByEmail(email).orElseGet(() -> {
                User created = new User();
                created.setEmail(email);
                created.setPassword(passwordEncoder.encode(BUYER_PASSWORD));
                created.setRole(UserRole.BUYER);
                return created;
            });
            buyer.setFullName(names[i]);
            buyer.setPhone(String.format(Locale.ROOT, "+216 %02d %03d %03d", 50 + i, 210 + i, 500 + i));
            buyer.setLocation(LOCATIONS[i] + ", Tunisia");
            buyer.setBio("E-biessa buyer account for testing saved parts, seller contact, and messaging.");
            buyers.add(userRepository.save(buyer));
        }
        return buyers;
    }

    private Map<String, Category> createCategories() {
        Map<String, Category> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getName, Function.identity(), (first, ignored) -> first));
        for (CategorySeed seed : CATEGORY_SEEDS) {
            categories.computeIfAbsent(seed.name(), name -> {
                Category category = new Category();
                category.setName(name);
                return categoryRepository.save(category);
            });
        }
        return categories;
    }

    private List<Vehicle> createVehicles() {
        List<Vehicle> existing = new ArrayList<>(vehicleRepository.findAll());
        for (VehicleSeed seed : VEHICLE_SEEDS) {
            boolean present = existing.stream().anyMatch(vehicle -> vehicle.getBrand().equals(seed.brand())
                    && vehicle.getModel().equals(seed.model()) && vehicle.getYear().equals(seed.year()));
            if (!present) {
                Vehicle vehicle = new Vehicle();
                vehicle.setBrand(seed.brand());
                vehicle.setModel(seed.model());
                vehicle.setYear(seed.year());
                vehicle.setEngine(seed.engine());
                vehicle.setFuelType(seed.fuelType());
                existing.add(vehicleRepository.save(vehicle));
            }
        }
        return existing;
    }

    private List<User> createSellers() {
        String[] names = {"Ahmed Ben Salem", "Sarra Trabelsi", "Youssef Gharbi"};
        String[] businesses = {"Carthage Auto Parts", "Sahel Motors", "Sfax Pièces Auto"};
        List<User> sellers = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            String email = String.format(Locale.ROOT, "seller%02d@e-biessa.tn", i + 1);
            int index = i;
            User seller = userRepository.findByEmail(email).orElseGet(() -> {
                User created = new User();
                created.setEmail(email);
                created.setPassword(passwordEncoder.encode(SELLER_PASSWORD));
                created.setRole(UserRole.SELLER);
                return created;
            });
            seller.setFullName(names[i]);
            seller.setBusinessName(businesses[i]);
            seller.setPhone(String.format(Locale.ROOT, "+216 %02d %03d %03d", 20 + i, 120 + i, 400 + i));
            seller.setLocation(LOCATIONS[i] + ", Tunisia");
            seller.setWebsite("https://seller" + String.format(Locale.ROOT, "%02d", i + 1) + ".example.com");
            seller.setBio("Automotive parts specialist offering inspected used components, clear fitment details, and responsive local service in " + LOCATIONS[index] + ".");
            sellers.add(userRepository.save(seller));
        }
        return sellers;
    }

    private int createParts(Map<String, Category> categories, List<Vehicle> vehicles, List<User> sellers) {
        long currentCount = partRepository.count();
        if (currentCount >= TARGET_PART_COUNT) return 0;
        int added = 0;
        for (int index = (int) currentCount; index < TARGET_PART_COUNT; index++) {
            PartSeed template = PART_SEEDS.get(index % PART_SEEDS.size());
            Vehicle vehicle = vehicles.get((index * 7 + index / PART_SEEDS.size()) % vehicles.size());
            User seller = sellers.get(index % sellers.size());
            String condition = CONDITIONS[index % CONDITIONS.length];
            String location = LOCATIONS[index % LOCATIONS.length];
            String oem = String.format(Locale.ROOT, "%s-%s-%04d", vehicle.getBrand().substring(0, Math.min(3, vehicle.getBrand().length())).toUpperCase(Locale.ROOT),
                    template.name().replaceAll("[^A-Za-z]", "").substring(0, 3).toUpperCase(Locale.ROOT), 1000 + index);

            Part part = new Part();
            part.setTitle(vehicle.getBrand() + " " + vehicle.getModel() + " " + template.name() + " " + vehicle.getYear());
            part.setDescription(description(template, vehicle, condition, seller, location, oem));
            part.setPrice(BigDecimal.valueOf(template.basePrice() + ((index * 37L) % Math.max(80, template.basePrice() / 3))));
            part.setCondition(condition);
            part.setCategory(categories.get(template.category()));
            part.setVehicle(vehicle);
            part.getCompatibleVehicles().add(vehicle);
            part.setSeller(seller);
            part.setManufacturer(vehicle.getBrand());
            part.setPartBrand(index % 3 == 0 ? "OEM" : "Aftermarket Pro");
            part.setOemNumber(oem);
            part.getAlternativeOemNumbers().add(oem + "-A");
            part.setPartPosition(positionFor(template.name()));
            part.setEngineCompatibility(vehicle.getEngine());
            part.setFuelCompatibility(vehicle.getFuelType());
            part.setWarrantyInformation(index % 2 == 0 ? "30-day seller warranty" : "No warranty");
            part.setDeliveryAvailable(index % 3 != 0);
            part.setCollectionAvailable(true);
            part.setNegotiable(index % 4 == 0);
            part.setLocation(location);
            part.setStatus(PartStatus.ACTIVE);
            part.setViews(12 + (index * 17) % 480);
            Part saved = partRepository.save(part);

            Image image = new Image();
            image.setPart(saved);
            image.setUrl(IMAGE_URLS[index % IMAGE_URLS.length]);
            image.setIsPrimary(true);
            image.setSortOrder(0);
            imageRepository.save(image);
            added++;
        }
        return added;
    }

    private int normalizeSeededPartPositions(List<User> sellers) {
        var sellerIds = sellers.stream().map(User::getId).collect(Collectors.toSet());
        int corrected = 0;
        for (Part part : partRepository.findAll()) {
            if (part.getSeller() == null || !sellerIds.contains(part.getSeller().getId())) continue;
            PartSeed template = PART_SEEDS.stream()
                    .filter(seed -> part.getTitle() != null && part.getTitle().contains(" " + seed.name() + " "))
                    .findFirst().orElse(null);
            if (template == null) continue;
            String expected = positionFor(template.name());
            if (!expected.equals(part.getPartPosition())) {
                part.setPartPosition(expected);
                corrected++;
            }
        }
        return corrected;
    }

    private String positionFor(String partName) {
        return switch (partName) {
            case "LED front headlight", "Front brake caliper", "Front shock absorber", "Steering rack",
                    "Radiator", "Air conditioning compressor", "Front bumper" -> "FRONT";
            case "Rear tail light" -> "REAR";
            case "Left door mirror", "Driver seat" -> "LEFT";
            default -> "UNIVERSAL";
        };
    }

    private String description(PartSeed template, Vehicle vehicle, String condition, User seller, String location, String oem) {
        return String.format(Locale.ROOT,
                "%s suitable for the %d %s %s with %s engine. Condition: %s. The component was inspected by %s and is supplied clean, complete, and ready for installation. OEM/reference number: %s. Available in %s; collection is preferred and nationwide delivery can be arranged. Buyers should compare the OEM number and vehicle details before purchase.",
                template.name(), vehicle.getYear(), vehicle.getBrand(), vehicle.getModel(), vehicle.getEngine(),
                condition, seller.getBusinessName(), oem, location);
    }

    private record CategorySeed(String name) {}
    private record VehicleSeed(String brand, String model, int year, String engine, String fuelType) {}
    private record PartSeed(String name, String category, long basePrice) {}
}
