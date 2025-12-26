package com.trading.hf;

import com.upstox.ApiClient;
import com.upstox.ApiException;
import com.upstox.Configuration;
import com.upstox.api.GetOptionContractResponse;
import com.upstox.api.InstrumentData;
import io.swagger.client.api.OptionsApi;
import com.upstox.auth.OAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UpstoxOptionContractService {
    private static final Logger logger = LoggerFactory.getLogger(UpstoxOptionContractService.class);
    private final String accessToken;
    private final OptionsApi apiInstance;

    public UpstoxOptionContractService(String accessToken) {
        this.accessToken = accessToken;
        
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        OAuth OAUTH2 = (OAuth) defaultClient.getAuthentication("OAUTH2");
        OAUTH2.setAccessToken(accessToken);
        
        this.apiInstance = new OptionsApi(defaultClient);
    }

    public CompletableFuture<List<InstrumentMaster.InstrumentDefinition>> fetchContracts(String underlyingKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // The SDK method getOptionContracts(instrument_key, expiry_date)
                // underlyingKey here is usually "NSE_INDEX|Nifty 50" or similar
                GetOptionContractResponse result = apiInstance.getOptionContracts(underlyingKey, null);
                
                if (result == null || result.getStatus() == null || !result.getStatus().toString().equalsIgnoreCase("success")) {
                    logger.error("Failed to fetch option contracts for {}. Status: {}", 
                            underlyingKey, (result != null ? result.getStatus() : "null"));
                    return new ArrayList<InstrumentMaster.InstrumentDefinition>();
                }

                List<InstrumentData> data = result.getData();
                if (data == null) {
                    return new ArrayList<>();
                }

                List<InstrumentMaster.InstrumentDefinition> contracts = new ArrayList<>();
                for (InstrumentData sdkContract : data) {
                    contracts.add(mapToInternal(sdkContract));
                }
                
                logger.info("Fetched {} option contracts for underlying: {}", contracts.size(), underlyingKey);
                return contracts;
            } catch (ApiException e) {
                logger.error("Exception when calling OptionsApi for {}: Status: {}, Body: {}", 
                        underlyingKey, e.getCode(), e.getResponseBody(), e);
                return new ArrayList<>();
            } catch (Exception e) {
                logger.error("Unexpected error fetching contracts for {}", underlyingKey, e);
                return new ArrayList<>();
            }
        });
    }

    private InstrumentMaster.InstrumentDefinition mapToInternal(InstrumentData sdk) {
        // Use manual mapping to a JsonObject to avoid GSON serialization issues with OffsetDateTime
        // and to precisely control the field names for GSON deserialization into InstrumentDefinition.
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("instrument_key", sdk.getInstrumentKey());
        obj.addProperty("trading_symbol", sdk.getTradingSymbol());
        obj.addProperty("underlying_key", sdk.getUnderlyingKey());
        obj.addProperty("strike_price", sdk.getStrikePrice());
        obj.addProperty("instrument_type", sdk.getInstrumentType());
        obj.addProperty("segment", sdk.getSegment());
        obj.addProperty("asset_symbol", sdk.getUnderlyingSymbol());
        
        if (sdk.getExpiry() != null) {
            // Convert OffsetDateTime to ISO local date string (e.g., "2024-12-25")
            // which our InstrumentDefinition.getExpiry() expects.
            obj.addProperty("expiry", sdk.getExpiry().toLocalDate().toString());
        }
        
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.fromJson(obj, InstrumentMaster.InstrumentDefinition.class);
    }
}
