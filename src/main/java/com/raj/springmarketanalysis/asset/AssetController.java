package com.raj.springmarketanalysis.asset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetRepository repo;

    public AssetController(AssetRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Asset> list() {
        return repo.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Asset create(@Valid @RequestBody CreateAssetRequest req) {
        // small normalization that's safe
        String symbol = req.symbol().trim().toUpperCase();
        String name = req.name().trim();
        String assetType = req.assetType().trim().toUpperCase();

        return repo.save(new Asset(symbol, name, assetType));
    }

    public record CreateAssetRequest(
            @NotBlank @Size(max = 20) String symbol,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 50) String assetType
    ) {}
}
