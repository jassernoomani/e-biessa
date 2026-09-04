from decimal import Decimal

from pydantic import BaseModel, Field, field_validator


class VehicleDocument(BaseModel):
    id: int | None = None
    brand: str
    model: str
    year: int | None = None
    engine: str | None = None
    fuelType: str | None = None


class ListingDocument(BaseModel):
    id: int
    title: str = Field(min_length=1, max_length=255)
    description: str | None = None
    price: Decimal = Field(ge=0)
    condition: str | None = None
    categoryName: str | None = None
    sellerId: int | None = None
    sellerName: str | None = None
    compatibleVehicles: list[VehicleDocument] = Field(default_factory=list)
    manufacturer: str | None = None
    partBrand: str | None = None
    oemNumber: str | None = None
    alternativeOemNumbers: list[str] = Field(default_factory=list)
    partPosition: str | None = None
    engineCompatibility: str | None = None
    fuelCompatibility: str | None = None
    warrantyInformation: str | None = None
    deliveryAvailable: bool = False
    collectionAvailable: bool = False
    negotiable: bool = False
    location: str | None = None
    status: str = "ACTIVE"

    def searchable_text(self) -> str:
        sections = [
            self.title,
            self.description,
            f"Category: {self.categoryName}" if self.categoryName else None,
            f"Condition: {self.condition}" if self.condition else None,
            f"Manufacturer: {self.manufacturer}" if self.manufacturer else None,
            f"Part brand: {self.partBrand}" if self.partBrand else None,
            f"OEM number: {self.oemNumber}" if self.oemNumber else None,
            f"Alternative OEM numbers: {', '.join(self.alternativeOemNumbers)}" if self.alternativeOemNumbers else None,
            f"Position: {self.partPosition}" if self.partPosition else None,
            f"Engine compatibility: {self.engineCompatibility}" if self.engineCompatibility else None,
            f"Fuel compatibility: {self.fuelCompatibility}" if self.fuelCompatibility else None,
            f"Warranty: {self.warrantyInformation}" if self.warrantyInformation else None,
            f"Location: {self.location}" if self.location else None,
        ]
        for vehicle in self.compatibleVehicles:
            sections.append("Compatible vehicle: " + " ".join(str(value) for value in (
                vehicle.brand, vehicle.model, vehicle.year, vehicle.engine, vehicle.fuelType
            ) if value))
        return "\n".join(section.strip() for section in sections if section and section.strip())


class BulkListingRequest(BaseModel):
    listings: list[ListingDocument]


class SearchFilters(BaseModel):
    brand: str | None = None
    model: str | None = None
    year: int | None = None
    condition: str | None = None
    category: str | None = None
    location: str | None = None
    fuelType: str | None = None
    partPosition: str | None = None
    minPrice: Decimal | None = Field(default=None, ge=0)
    maxPrice: Decimal | None = Field(default=None, ge=0)

    @field_validator("maxPrice")
    @classmethod
    def validate_price_range(cls, value: Decimal | None, info):
        minimum = info.data.get("minPrice")
        if value is not None and minimum is not None and value < minimum:
            raise ValueError("maxPrice must be greater than or equal to minPrice")
        return value


class SearchRequest(BaseModel):
    query: str = Field(min_length=2, max_length=500)
    limit: int = Field(default=12, ge=1, le=50)
    minimumScore: float = Field(default=0.50, ge=0, le=1)
    filters: SearchFilters = Field(default_factory=SearchFilters)


class ExtractedConstraints(BaseModel):
    brand: str | None = None
    model: str | None = None
    year: int | None = None
    fuelType: str | None = None
    position: str | None = None
    category: str | None = None
    partType: str | None = None


class SearchHit(BaseModel):
    listingId: int
    score: float
    rawScore: float
    title: str
    price: Decimal
    sellerName: str | None = None
    categoryName: str | None = None
    location: str | None = None
    compatibleVehicles: list[VehicleDocument] = Field(default_factory=list)
    matchReasons: list[str] = Field(default_factory=list)


class GroundedAnswer(BaseModel):
    summary: str
    recommendedListingIds: list[int] = Field(default_factory=list)
    cautions: list[str] = Field(default_factory=list)
    generatedBy: str = "deterministic"


class SearchResponse(BaseModel):
    query: str
    hits: list[SearchHit]
    tookMs: int
    extractedConstraints: ExtractedConstraints
    answer: GroundedAnswer


class IndexResponse(BaseModel):
    indexed: int = 0
    deleted: int = 0
