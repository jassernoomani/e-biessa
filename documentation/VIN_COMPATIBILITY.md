# VIN-assisted vehicle selection

E-biessa can decode a signed-in buyer's 17-character VIN through the public NHTSA vPIC service. Spring Boot validates the VIN, calls the decoder with a short timeout, caches successful responses for 24 hours, and maps the decoded make, model, year, and fuel type to existing local `Vehicle` rows.

The VIN itself is not persisted. Angular only adds matching local vehicle IDs to the buyer's unsaved preference selection; the buyer must still press **Save preferences**. If no exact local configuration exists, the UI keeps the decoded description visible and asks the buyer to choose manually.

This is a discovery aid, not a fitment guarantee. Listing compatibility and seller confirmation remain authoritative. The NHTSA dataset is primarily US-oriented, so some European-market VINs may decode incompletely.

## Configuration

```properties
VIN_DECODER_ENABLED=true
VIN_DECODER_BASE_URL=https://vpic.nhtsa.dot.gov/api
VIN_DECODER_TIMEOUT_SECONDS=8
VIN_DECODER_CACHE_HOURS=24
```

`GET /api/vehicles/vin/{vin}` requires authentication. No NHTSA API key is required.
