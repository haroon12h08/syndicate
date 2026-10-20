package com.syndicate.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;

public class SamplePdfGenerator {

    @Test
    void generateSampleAuditReportPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            // Page 1: Title & Overview
            PDPage p1 = new PDPage(PDRectangle.LETTER);
            doc.addPage(p1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) {
                cs.beginText();
                cs.setFont(bold, 20);
                cs.newLineAtOffset(50, 720);
                cs.showText("TRINITY CAPITAL LIMITED");
                cs.endText();

                cs.beginText();
                cs.setFont(bold, 14);
                cs.newLineAtOffset(50, 690);
                cs.showText("INDEPENDENT AUDITOR EXAMINATION REPORT");
                cs.endText();

                cs.beginText();
                cs.setFont(regular, 11);
                cs.newLineAtOffset(50, 650);
                cs.showText("Restated Financial Statements for SME Initial Public Offering");
                cs.newLineAtOffset(0, -20);
                cs.showText("Auditor: Al-Qasim & Partners, Chartered Accountants (Firm Reg: 104522W)");
                cs.newLineAtOffset(0, -20);
                cs.showText("Engagement Lead: Amina Al-Qasim, FCA | Partner");
                cs.newLineAtOffset(0, -30);
                cs.showText("Executive Diligence Summary:");
                cs.newLineAtOffset(0, -18);
                cs.showText("We have examined the restated standalone financial statements of Trinity Capital");
                cs.newLineAtOffset(0, -18);
                cs.showText("Limited comprising the Balance Sheet as at March 31, 2026, 2025, and 2024, and the");
                cs.newLineAtOffset(0, -18);
                cs.showText("Statement of Profit and Loss, Cash Flows, and Summary of Significant Accounting Policies.");
                cs.newLineAtOffset(0, -25);
                cs.showText("The restatement has been performed in accordance with Section 26 of Part I of Chapter III");
                cs.newLineAtOffset(0, -18);
                cs.showText("of the Companies Act, 2013 and Securities and Exchange Board of India (ICDR) Regulations.");
                cs.endText();
            }

            // Page 2: Balance Sheet
            PDPage p2 = new PDPage(PDRectangle.LETTER);
            doc.addPage(p2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
                cs.beginText();
                cs.setFont(bold, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("BALANCE SHEET AS AT MARCH 31, 2026");
                cs.endText();

                cs.beginText();
                cs.setFont(regular, 10);
                cs.newLineAtOffset(50, 690);
                cs.showText("Particulars                                   FY2026 (INR)         FY2025 (INR)");
                cs.newLineAtOffset(0, -20);
                cs.setFont(bold, 11);
                cs.showText("I. EQUITY AND LIABILITIES");
                cs.setFont(regular, 10);
                cs.newLineAtOffset(0, -18);
                cs.showText("Shareholder Funds:");
                cs.newLineAtOffset(0, -16);
                cs.showText("  (a) Share Capital                          10,00,00,000         10,00,00,000");
                cs.newLineAtOffset(0, -16);
                cs.showText("  (b) Reserves & Surplus                     32,18,00,000         22,50,00,000");
                cs.newLineAtOffset(0, -20);
                cs.showText("Non-Current Liabilities:                      8,50,00,000          6,20,00,000");
                cs.newLineAtOffset(0, -16);
                cs.showText("Current Liabilities:                         14,32,00,000         11,10,00,000");
                cs.newLineAtOffset(0, -24);
                cs.setFont(bold, 11);
                cs.showText("TOTAL EQUITY & LIABILITIES:                  65,00,00,000         49,80,00,000");
                cs.newLineAtOffset(0, -30);
                cs.showText("II. ASSETS");
                cs.setFont(regular, 10);
                cs.newLineAtOffset(0, -18);
                cs.showText("Non-Current Assets:");
                cs.newLineAtOffset(0, -16);
                cs.showText("  (a) Property, Plant & Equipment            22,50,00,000         18,40,00,000");
                cs.newLineAtOffset(0, -16);
                cs.showText("  (b) Non-Current Investments                 4,50,00,000          3,00,00,000");
                cs.newLineAtOffset(0, -20);
                cs.showText("Current Assets:");
                cs.newLineAtOffset(0, -16);
                cs.showText("  (a) Trade Receivables                      18,40,00,000         14,20,00,000");
                cs.newLineAtOffset(0, -16);
                cs.showText("  (b) Cash and Bank Balances                  9,60,00,000          6,20,00,000");
                cs.newLineAtOffset(0, -16);
                cs.showText("  (c) Short-term Loans & Advances            10,00,00,000          8,00,00,000");
                cs.newLineAtOffset(0, -24);
                cs.setFont(bold, 11);
                cs.showText("TOTAL ASSETS:                                65,00,00,000         49,80,00,000");
                cs.endText();
            }

            // Page 3: Statement of Profit and Loss
            PDPage p3 = new PDPage(PDRectangle.LETTER);
            doc.addPage(p3);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p3)) {
                cs.beginText();
                cs.setFont(bold, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("STATEMENT OF RESTATED PROFIT AND LOSS");
                cs.endText();

                cs.beginText();
                cs.setFont(regular, 10);
                cs.newLineAtOffset(50, 690);
                cs.showText("Particulars                                   FY2026 (INR)         FY2025 (INR)");
                cs.newLineAtOffset(0, -20);
                cs.setFont(bold, 11);
                cs.showText("REVENUE FROM OPERATIONS:                     82,45,00,000         61,20,00,000");
                cs.setFont(regular, 10);
                cs.newLineAtOffset(0, -18);
                cs.showText("Other Income                                  1,25,00,000            95,00,000");
                cs.newLineAtOffset(0, -20);
                cs.setFont(bold, 11);
                cs.showText("TOTAL REVENUE:                               83,70,00,000         62,15,00,000");
                cs.newLineAtOffset(0, -24);
                cs.showText("EXPENSES:");
                cs.setFont(regular, 10);
                cs.newLineAtOffset(0, -16);
                cs.showText("Operating & Service Costs                    52,10,00,000         39,40,00,000");
                cs.newLineAtOffset(0, -16);
                cs.showText("Employee Benefits Expense                    12,80,00,000          9,60,00,000");
                cs.newLineAtOffset(0, -16);
                cs.showText("Finance Costs                                 1,90,00,000          1,50,00,000");
                cs.newLineAtOffset(0, -16);
                cs.showText("Depreciation and Amortization                 1,50,00,000          1,25,00,000");
                cs.newLineAtOffset(0, -24);
                cs.setFont(bold, 11);
                cs.showText("RESTATED EBITDA:                             16,80,00,000         11,90,00,000");
                cs.newLineAtOffset(0, -20);
                cs.showText("PROFIT BEFORE TAX:                           15,40,00,000         10,40,00,000");
                cs.newLineAtOffset(0, -20);
                cs.showText("RESTATED NET PROFIT:                         11,40,00,000          7,80,00,000");
                cs.endText();
            }

            // Page 4: Auditor Sign-off
            PDPage p4 = new PDPage(PDRectangle.LETTER);
            doc.addPage(p4);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p4)) {
                cs.beginText();
                cs.setFont(bold, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("AUDITOR OPINION & STATUTORY CERTIFICATION");
                cs.endText();

                cs.beginText();
                cs.setFont(regular, 10);
                cs.newLineAtOffset(50, 680);
                cs.showText("In our opinion and to the best of our information and according to the explanations");
                cs.newLineAtOffset(0, -16);
                cs.showText("given to us, the aforesaid restated financial statements read with notes thereon give");
                cs.newLineAtOffset(0, -16);
                cs.showText("a true and fair view in conformity with the accounting principles generally accepted in India:");
                cs.newLineAtOffset(0, -20);
                cs.showText("(a) In the case of the Balance Sheet, of the state of affairs as at March 31, 2026;");
                cs.newLineAtOffset(0, -16);
                cs.showText("(b) In the case of the Statement of Profit and Loss, of the restated profit for the period; and");
                cs.newLineAtOffset(0, -16);
                cs.showText("(c) In the case of the Statement of Cash Flows, of the cash flows for the period ended.");
                cs.newLineAtOffset(0, -35);
                cs.setFont(bold, 11);
                cs.showText("For Al-Qasim & Partners");
                cs.setFont(regular, 10);
                cs.newLineAtOffset(0, -16);
                cs.showText("Chartered Accountants (FRN: 104522W)");
                cs.newLineAtOffset(0, -25);
                cs.showText("Amina Al-Qasim, FCA");
                cs.newLineAtOffset(0, -16);
                cs.showText("Partner | Membership No: 084920");
                cs.newLineAtOffset(0, -16);
                cs.showText("UDIN: 26084920AAAAAB1234");
                cs.newLineAtOffset(0, -16);
                cs.showText("Date: May 12, 2026 | Place: Mumbai");
                cs.endText();
            }

            File dest = new File("/home/haroon/Desktop/syndicate/sample-audit-report-fy26.pdf");
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                doc.save(fos);
            }
            System.out.println("GENERATED_PDF_AT: " + dest.getAbsolutePath());
        }
    }
}
