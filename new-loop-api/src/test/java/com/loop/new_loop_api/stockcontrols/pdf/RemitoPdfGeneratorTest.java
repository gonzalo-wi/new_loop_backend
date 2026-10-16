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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RemitoPdfGeneratorTest {

    private final RemitoPdfGenerator generator = new RemitoPdfGenerator();

    @Test
    void shouldRenderOnA5Paper() throws IOException {
        try (var reader = new PdfReader(generator.generate(controlWithItems(5)))) {
            var page = reader.getPageSize(1);
            assertThat(page.getWidth()).isEqualTo(PageSize.A5.getWidth());
            assertThat(page.getHeight()).isEqualTo(PageSize.A5.getHeight());
        }
    }

    @Test
    void shouldFitTypicalLoadWithSignaturesOnSinglePage() throws IOException {
        try (var reader = new PdfReader(generator.generate(controlWithItems(12)))) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            var text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Controló", "Repartidor");
        }
    }

    @Test
    void shouldSpillOverToMorePagesOnlyForVeryLongLoads() throws IOException {
        try (var reader = new PdfReader(generator.generate(controlWithItems(60)))) {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            var lastPage = new PdfTextExtractor(reader).getTextFromPage(reader.getNumberOfPages());
            assertThat(lastPage).contains("Controló", "Repartidor");
        }
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
