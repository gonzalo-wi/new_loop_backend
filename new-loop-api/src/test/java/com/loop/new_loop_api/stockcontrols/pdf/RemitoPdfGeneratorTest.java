package com.loop.new_loop_api.stockcontrols.pdf;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.products.entity.Product;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;
import org.junit.jupiter.api.Test;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemitoPdfGeneratorTest {

    private final RemitoPdfGenerator generator = new RemitoPdfGenerator();

    @Test
    void shouldRenderOnA5Paper() throws IOException {
        try (var reader = new PdfReader(generator.generate(controlWithItems(5), false))) {
            var page = reader.getPageSize(1);
            assertThat(page.getWidth()).isEqualTo(PageSize.A5.getWidth());
            assertThat(page.getHeight()).isEqualTo(PageSize.A5.getHeight());
        }
    }

    @Test
    void shouldFitTypicalLoadWithSignaturesOnSinglePage() throws IOException {
        try (var reader = new PdfReader(generator.generate(controlWithItems(12), false))) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            var text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Controló", "Repartidor");
        }
    }

    @Test
    void shouldSpillOverToMorePagesOnlyForVeryLongLoads() throws IOException {
        try (var reader = new PdfReader(generator.generate(controlWithItems(60), false))) {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            var lastPage = new PdfTextExtractor(reader).getTextFromPage(reader.getNumberOfPages());
            assertThat(lastPage).contains("Controló", "Repartidor");
        }
    }

    @Test
    void shouldPrintCompanyDataOnNormalRemito() throws IOException {
        var text = firstPageText(generator.generate(controlWithItems(3), false));

        assertThat(text).contains("El Jumillano S.A", "30537882871", "REMITO DE SALIDA", "Cód. 91");
    }

    @Test
    void shouldLeaveOutEveryCompanyDataOnHolidayRemito() throws IOException {
        var text = firstPageText(generator.generate(controlWithItems(3), true));

        assertThat(text).doesNotContain("El Jumillano S.A", "Santiago de Liniers", "0800-333-3090",
                "30537882871", "CUIT", "IIBB", "Condición IVA", "Cód. 91", "Documento no válido como factura",
                "REMITO DE SALIDA");
        assertThat(text).contains("REMITO", "R202", "367014", "Reparto", "49");
    }

    @Test
    void shouldShowBulkProductsInBulksAndOthersInUnits() throws IOException {
        var control = controlWithItems(0);
        addItem(control, "SODA", "Soda 2 litros", 12, 6);
        addItem(control, "SODA5", "Soda 1,5 litros", 15, 6);
        addItem(control, "BID20", "Bidón 20 litros", 7, null);

        var text = firstPageText(generator.generate(control, false));

        // The extractor glues the cells of a row together: name then total.
        assertThat(text).contains("Soda 2 litros2\n");
        assertThat(text).contains("Soda 1,5 litros15 u\n");
        assertThat(text).contains("Bidón 20 litros7\n");
    }

    @Test
    void shouldPutEachRemitoOfBatchOnItsOwnPageInOrder() throws IOException {
        var pages = List.of(
                new RemitoPdfGenerator.RemitoPage(controlForRoute("11"), false),
                new RemitoPdfGenerator.RemitoPage(controlForRoute("22"), true),
                new RemitoPdfGenerator.RemitoPage(controlForRoute("33"), false));

        try (var reader = new PdfReader(generator.generate(pages))) {
            assertThat(reader.getNumberOfPages()).isEqualTo(3);
            var extractor = new PdfTextExtractor(reader);
            assertThat(extractor.getTextFromPage(1)).contains("Reparto 11").contains("El Jumillano S.A");
            assertThat(extractor.getTextFromPage(2)).contains("Reparto 22").doesNotContain("El Jumillano S.A");
            assertThat(extractor.getTextFromPage(3)).contains("Reparto 33").contains("El Jumillano S.A");
        }
    }

    @Test
    void shouldRefuseEmptyBatch() {
        assertThatThrownBy(() -> generator.generate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String firstPageText(byte[] pdf) throws IOException {
        try (var reader = new PdfReader(pdf)) {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        }
    }

    private StockControl controlForRoute(String routeCode) {
        var control = controlWithItems(2);
        control.setRoute(Route.builder().code(routeCode).build());
        return control;
    }

    private void addItem(StockControl control, String code, String name, int total, Integer packQuantity) {
        control.getItems().add(StockControlItem.builder()
                .stockControl(control)
                .product(Product.builder().code(code).name(name).packQuantity(packQuantity).build())
                .totalQuantity(total)
                .build());
    }

    private StockControl controlWithItems(int count) {
        var branch = Branch.builder()
                .name("Ciudadela")
                .companyName("El Jumillano S.A")
                .address("Santiago de Liniers 3229")
                .locality("Ciudadela")
                .province("Buenos Aires")
                .phone("0800-333-3090")
                .cuit("30537882871")
                .vatCondition("3")
                .build();
        var control = StockControl.builder()
                .type(ControlType.EXIT)
                .branch(branch)
                .route(Route.builder().code("49").build())
                .controlDate(LocalDate.of(2026, 10, 16))
                .confirmedAt(LocalDateTime.of(2026, 10, 16, 7, 30))
                .aguasFormulario("R202")
                .aguasNroRemito(367014L)
                .build();
        IntStream.rangeClosed(1, count).forEach(i -> control.getItems().add(StockControlItem.builder()
                .stockControl(control)
                .product(Product.builder().code("P" + i).name("Bidón 20 litros retornable " + i).build())
                .totalQuantity(i)
                .build()));
        return control;
    }
}
