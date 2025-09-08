/* 
 * Copyright 2016-2025, Michael Büchner <m.buechner@dnb.de>
 * Deutsche Digitale Bibliothek
 * c/o Deutsche Nationalbibliothek
 * Informationsinfrastruktur
 * Adickesallee 1, D-60322 Frankfurt am Main 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ddb.beacons;

/**
 *
 * @author michael
 */
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.h2.mvstore.MVStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class CommonsLicensesApp {

    private final String MAP_GND2FILE = "gnd2file";
    private final String MAP_FILE2GNDS = "file2gnds";
    private final String MAP_GND2LIC = "gnd2license";

    private MVStore store;
    private OkHttpClient httpClient;

    public static void main(String[] args) {
        SpringApplication.run(CommonsLicensesApp.class, args);
    }

    @Bean
    MVStore getMvStore() throws IOException {
        if (store == null) {
            final File tmpFile = File.createTempFile("wd2beacon", ".db");
            tmpFile.deleteOnExit();
            store = new MVStore.Builder().fileName(tmpFile.getAbsolutePath()).open();
        }
        return store;
    }

    @Bean
    OkHttpClient getOkHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(java.time.Duration.ofSeconds(30))
                    .readTimeout(java.time.Duration.ofMinutes(5))
                    .callTimeout(java.time.Duration.ofMinutes(10))
                    .build();
        }
        return httpClient;
    }

    @PreDestroy
    private void destroy() {
        log.info("Destroy callback triggered: Closing database ...");
        try {
            store.close();
            httpClient.dispatcher().cancelAll();
        } catch (Exception e) {
            log.error("Could not close connection to database. {}", e.getMessage());
        }
    }

    /**
     * @return the MAP_GND2FILE
     */
    @Bean
    public String getMAP_GND2FILE() {
        return MAP_GND2FILE;
    }

    /**
     * @return the MAP_FILE2GNDS
     */
    @Bean
    public String getMAP_FILE2GNDS() {
        return MAP_FILE2GNDS;
    }

    /**
     * @return the MAP_GND2LIC
     */
    @Bean
    public String getMAP_GND2LIC() {
        return MAP_GND2LIC;
    }
}
