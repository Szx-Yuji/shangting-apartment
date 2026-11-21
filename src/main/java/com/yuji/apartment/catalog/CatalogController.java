package com.yuji.apartment.catalog;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.yuji.apartment.catalog.CatalogDtos.*;

@RestController
public class CatalogController {
    private final CatalogService service;
    private final ImageStorage storage;
    public CatalogController(CatalogService service,ImageStorage storage){this.service=service;this.storage=storage;}
    @GetMapping("/api/public/listings")
    public Page<Listing> listings(@RequestParam(required=false) String city,@RequestParam(required=false) String q,
            @RequestParam(required=false) Integer minRent,@RequestParam(required=false) Integer maxRent,
            @RequestParam(defaultValue="newest") String sort,@RequestParam(defaultValue="1") long page,
            @RequestParam(defaultValue="12") long pageSize){
        return service.listings(false,null,city,q,minRent,maxRent,null,sort,page,pageSize);
    }
    @GetMapping("/api/public/listings/{id}") public Listing listing(@PathVariable Long id){return service.listing(id,false);}
    @GetMapping("/api/admin/catalog/apartments") public Page<Apartment> apartments(
            @RequestParam(required=false) String city,@RequestParam(defaultValue="1") long page,
            @RequestParam(defaultValue="10") long pageSize){return service.apartments(city,page,pageSize);}
    @PostMapping("/api/admin/catalog/apartments") @ResponseStatus(HttpStatus.CREATED)
    public Apartment createApartment(@Valid @RequestBody ApartmentInput body){return service.saveApartment(null,body);}
    @PutMapping("/api/admin/catalog/apartments/{id}")
    public Apartment updateApartment(@PathVariable Long id,@Valid @RequestBody ApartmentInput body){return service.saveApartment(id,body);}
    @DeleteMapping("/api/admin/catalog/apartments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApartment(@PathVariable Long id){service.deleteApartment(id);}
    @GetMapping("/api/admin/catalog/rooms") public Page<Listing> rooms(@RequestParam(required=false) Long apartmentId,
            @RequestParam(required=false) String city,@RequestParam(required=false) String q,
            @RequestParam(required=false) Boolean published,@RequestParam(defaultValue="1") long page,
            @RequestParam(defaultValue="10") long pageSize){
        return service.listings(true,apartmentId,city,q,null,null,published,"newest",page,pageSize);
    }
    @PostMapping("/api/admin/catalog/rooms") @ResponseStatus(HttpStatus.CREATED)
    public Listing createRoom(@Valid @RequestBody RoomInput body){return service.saveRoom(null,body);}
    @PutMapping("/api/admin/catalog/rooms/{id}")
    public Listing updateRoom(@PathVariable Long id,@Valid @RequestBody RoomInput body){return service.saveRoom(id,body);}
    @DeleteMapping("/api/admin/catalog/rooms/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(@PathVariable Long id){service.deleteRoom(id);}
    @PostMapping("/api/admin/catalog/uploads") @ResponseStatus(HttpStatus.CREATED)
    public ImageUpload upload(@RequestParam("file") MultipartFile file){return new ImageUpload(storage.save(file));}
}
