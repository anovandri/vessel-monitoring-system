package com.kreasipositif.vms.collector.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ParserRegistry Tests")
class ParserRegistryTest {

    private ParserRegistry registry;
    private TestParser parser1;
    private TestParser parser2;

    @BeforeEach
    void setUp() {
        registry = new ParserRegistry();
        parser1 = new TestParser("TYPE_1", "marker1");
        parser2 = new TestParser("TYPE_2", "marker2");
    }

    @Test
    @DisplayName("Should register parser successfully")
    void shouldRegisterParser() {
        // When
        registry.registerParser(parser1);

        // Then
        assertThat(registry.hasParser("TYPE_1")).isTrue();
        assertThat(registry.getParser("TYPE_1"))
                .isPresent();
    }

    @Test
    @DisplayName("Should not register parser with duplicate type")
    void shouldNotRegisterDuplicateParser() {
        // Given
        registry.registerParser(parser1);

        // When/Then
        // Should log a warning but not throw exception - just replace
        registry.registerParser(parser1);
        assertThat(registry.hasParser("TYPE_1")).isTrue();
    }

    @Test
    @DisplayName("Should get parser by type")
    void shouldGetParserByType() {
        // Given
        registry.registerParser(parser1);
        registry.registerParser(parser2);

        // When
        var result = registry.getParser("TYPE_1");

        // Then
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Should return empty for unknown parser type")
    void shouldReturnEmptyForUnknownType() {
        // When
        var result = registry.getParser("UNKNOWN");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should parse data with correct parser")
    void shouldParseDataWithCorrectParser() throws DataParser.ParsingException {
        // Given
        registry.registerParser(parser1);
        registry.registerParser(parser2);
        String rawData = "{\"type\": \"marker1\", \"value\": \"test\"}";

        // When
        TestData result = registry.parse("TYPE_1", rawData);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getValue()).isEqualTo("test");
    }

    @Test
    @DisplayName("Should throw exception when no parser found")
    void shouldThrowExceptionWhenNoParserFound() {
        // Given
        String rawData = "{\"type\": \"unknown\"}";

        // When/Then
        assertThatThrownBy(() -> registry.parse("UNKNOWN", rawData))
                .isInstanceOf(DataParser.ParsingException.class)
                .hasMessageContaining("No parser");
    }

    @Test
    @DisplayName("Should get all registered types")
    void shouldGetAllRegisteredTypes() {
        // Given
        registry.registerParser(parser1);
        registry.registerParser(parser2);

        // When
        var types = registry.getRegisteredTypes();

        // Then
        assertThat(types).hasSize(2)
                .contains("TYPE_1", "TYPE_2");
    }

    @Test
    @DisplayName("Should check if parser exists")
    void shouldCheckIfParserExists() {
        // Given
        registry.registerParser(parser1);

        // When/Then
        assertThat(registry.hasParser("TYPE_1")).isTrue();
        assertThat(registry.hasParser("UNKNOWN")).isFalse();
    }

    @Test
    @DisplayName("Should clear all parsers")
    void shouldClearAllParsers() {
        // Given
        registry.registerParser(parser1);
        registry.registerParser(parser2);

        // When
        registry.clearAll();

        // Then
        assertThat(registry.getRegisteredTypes()).isEmpty();
        assertThat(registry.hasParser("TYPE_1")).isFalse();
    }

    // Test implementation of DataParser
    private static class TestParser implements DataParser<TestData> {
        private final String dataType;
        private final String marker;

        public TestParser(String dataType, String marker) {
            this.dataType = dataType;
            this.marker = marker;
        }

        @Override
        public TestData parse(String rawData) throws ParsingException {
            if (!rawData.contains(marker)) {
                throw new ParsingException("Invalid data for " + dataType);
            }
            return new TestData("test");
        }

        @Override
        public boolean canParse(String rawData) {
            return rawData.contains(marker);
        }

        @Override
        public String getDataType() {
            return dataType;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }
    }

    // Test data class
    private static class TestData {
        private final String value;

        public TestData(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
