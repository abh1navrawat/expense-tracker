package com.tracker.expense.service;

import com.tracker.expense.model.Expense;
import com.tracker.expense.model.ReportJob;
import com.tracker.expense.repository.ExpenseRepository;
import com.tracker.expense.repository.ReportJobRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ReportJobRepository reportJobRepository;

    @Value("${reports.storage.dir}")
    private String reportsStorageDir;

    @Async("reportTaskExecutor")
    public void generateExcelReportAsync(UUID trackingId, Long userId) {
        logger.info("Executing async Apache POI workbook compilation task. UUID: {}", trackingId);

        ReportJob job = reportJobRepository.findByTrackingId(trackingId).orElse(null);
        if (job == null) {
            logger.error("Async report worker aborted: ReportJob UUID {} not found in database.", trackingId);
            return;
        }

        try {
            // Retrieve user's transaction dataset
            List<Expense> expenses = expenseRepository.findByUserIdOrderByExpenseDateDesc(userId);

            // Establish secure destination file structure
            File directory = new File(reportsStorageDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String filename = "Report_" + trackingId.toString() + ".xlsx";
            File outputFile = new File(directory, filename);

            // Open Apache POI Workbook writer
            try (Workbook workbook = new XSSFWorkbook()) {
                
                // --- TAB 1: OVERVIEW DASHBOARD ---
                Sheet summarySheet = workbook.createSheet("Dashboard Overview");
                
                // Design professional typography styling
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setFontHeightInPoints((short) 12);
                headerFont.setColor(IndexedColors.WHITE.getIndex());

                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);

                // Row 0: Banner header
                Row bannerRow = summarySheet.createRow(0);
                Cell bannerCell = bannerRow.createCell(0);
                bannerCell.setCellValue("Expense Tracker Platform Analytics");
                bannerCell.setCellStyle(headerStyle);
                summarySheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

                // Perform mathematical aggregates
                BigDecimal grandTotal = BigDecimal.ZERO;
                Map<String, BigDecimal> categoryBreakdowns = new HashMap<>();
                for (Expense e : expenses) {
                    grandTotal = grandTotal.add(e.getConvertedAmountBase());
                    String catName = e.getCategory().getName();
                    categoryBreakdowns.put(catName, categoryBreakdowns.getOrDefault(catName, BigDecimal.ZERO).add(e.getConvertedAmountBase()));
                }

                // Row 2: General KPI cards
                Row kpiRow = summarySheet.createRow(2);
                kpiRow.createCell(0).setCellValue("Total Cumulative Spend (Base: " + job.getUser().getBaseCurrency() + ")");
                kpiRow.createCell(1).setCellValue(grandTotal.doubleValue());

                // Row 4: Grid headers for Category totals
                Row catGridHeader = summarySheet.createRow(4);
                catGridHeader.createCell(0).setCellValue("Category Segment");
                catGridHeader.createCell(1).setCellValue("Aggregated Cost");
                catGridHeader.getCell(0).setCellStyle(headerStyle);
                catGridHeader.getCell(1).setCellStyle(headerStyle);

                int cursorRow = 5;
                for (Map.Entry<String, BigDecimal> pair : categoryBreakdowns.entrySet()) {
                    Row dataRow = summarySheet.createRow(cursorRow++);
                    dataRow.createCell(0).setCellValue(pair.getKey());
                    dataRow.createCell(1).setCellValue(pair.getValue().doubleValue());
                }

                // --- TAB 2: DETAILED LEDGER ---
                Sheet dataSheet = workbook.createSheet("Transactions Ledger");
                Row colHeaderRow = dataSheet.createRow(0);
                String[] colHeaders = {"Expense ID", "Expense Date", "Category", "Description", "Raw Cost", "Raw Currency", "Converted Cost (" + job.getUser().getBaseCurrency() + ")"};
                
                for (int i = 0; i < colHeaders.length; i++) {
                    Cell c = colHeaderRow.createCell(i);
                    c.setCellValue(colHeaders[i]);
                    c.setCellStyle(headerStyle);
                }

                int ledgerCursor = 1;
                for (Expense e : expenses) {
                    Row r = dataSheet.createRow(ledgerCursor++);
                    r.createCell(0).setCellValue(e.getId());
                    r.createCell(1).setCellValue(e.getExpenseDate().toString());
                    r.createCell(2).setCellValue(e.getCategory().getName());
                    r.createCell(3).setCellValue(e.getDescription());
                    r.createCell(4).setCellValue(e.getAmount().doubleValue());
                    r.createCell(5).setCellValue(e.getCurrency());
                    r.createCell(6).setCellValue(e.getConvertedAmountBase().doubleValue());
                }

                // Auto-scale grid sheets
                for (int i = 0; i < colHeaders.length; i++) {
                    dataSheet.autoSizeColumn(i);
                }
                summarySheet.autoSizeColumn(0);
                summarySheet.autoSizeColumn(1);

                // Write sheet stream to filesystem
                try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
                    workbook.write(fileOutputStream);
                }
            }

            // Mark ReportJob database status as COMPLETED
            job.setStatus("COMPLETED");
            job.setFilePath(outputFile.getAbsolutePath());
            reportJobRepository.save(job);
            logger.info("Async Excel POI file written successfully: {}", outputFile.getAbsolutePath());

        } catch (Exception e) {
            logger.error("Failed to generate async Excel POI workbook", e);
            job.setStatus("FAILED");
            reportJobRepository.save(job);
        }
    }
}
