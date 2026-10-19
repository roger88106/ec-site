package com.ecsite.module.region.presentation;

import com.ecsite.IntegrationTest;
import com.ecsite.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 店舗 API の地域の操作の受け入れテスト（T016-01-09）。3 つとも token なしで使える。
 */
@IntegrationTest
@AutoConfigureMockMvc
class StoreRegionApiTest {

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("AC-250 都道府県の一覧を、token なしで、コードの順に返す")
    void prefectures() throws Exception {
        mockMvc.perform(get("/api/store/prefectures").with(TestTokens.none()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(47)))
                .andExpect(jsonPath("$.items[0].code").value("01"))
                .andExpect(jsonPath("$.items[0].name").value("北海道"))
                .andExpect(jsonPath("$.items[46].code").value("47"));
    }

    @Test
    @DisplayName("AC-250 都道府県の市区町村の一覧を、token なしで返す")
    void municipalities() throws Exception {
        mockMvc.perform(get("/api/store/prefectures/13/municipalities").with(TestTokens.none()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("13101"))
                .andExpect(jsonPath("$.items[0].name").value("千代田区"));
    }

    @Test
    @DisplayName("ない都道府県の市区町村の一覧は 404")
    void unknownPrefecture() throws Exception {
        mockMvc.perform(get("/api/store/prefectures/48/municipalities").with(TestTokens.none()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-253 郵便番号で引くと、都道府県・市区町村・町域を、token なしで返す")
    void postalCode() throws Exception {
        mockMvc.perform(get("/api/store/postal-codes/1000001").with(TestTokens.none()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].prefecture.code").value("13"))
                .andExpect(jsonPath("$.items[0].prefecture.name").value("東京都"))
                .andExpect(jsonPath("$.items[0].municipality.code").value("13101"))
                .andExpect(jsonPath("$.items[0].municipality.name").value("千代田区"))
                .andExpect(jsonPath("$.items[0].townName").value("千代田"));
    }

    @Test
    @DisplayName("AC-254 1 つの郵便番号に複数の住所があれば、すべてを返す")
    void multipleAddresses() throws Exception {
        mockMvc.perform(get("/api/store/postal-codes/0691334").with(TestTokens.none()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].municipality.code", everyItem(is("01428"))));
    }

    @Test
    @DisplayName("AC-256 ない郵便番号は、200 で空の一覧を返す")
    void unknownPostalCode() throws Exception {
        mockMvc.perform(get("/api/store/postal-codes/9999999").with(TestTokens.none()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("形の正しくない郵便番号は 400")
    void invalidPostalCode() throws Exception {
        mockMvc.perform(get("/api/store/postal-codes/100-0001").with(TestTokens.none()))
                .andExpect(status().isBadRequest());
    }
}
