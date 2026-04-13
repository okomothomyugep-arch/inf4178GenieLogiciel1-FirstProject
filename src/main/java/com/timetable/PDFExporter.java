package com.timetable;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class PDFExporter {
    
    private static final String[] DAYS = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"};
    private static final String[] PERIODS = {"07:00-09:55", "10:05-12:55", "13:05-15:55", "16:05-18:55", "19:05-21:55"};
    private static final String[] PERIOD_LABELS = {"Période 1", "Période 2", "Période 3", "Période 4", "Période 5"};
    
    // Définir les couleurs manuellement (évite BaseColor)
    private static final Color LIGHT_GRAY = new Color(200, 200, 200);
    private static final Color LIGHT_GREEN = new Color(200, 230, 200);
    private static final Color VERY_LIGHT_GRAY = new Color(245, 245, 245);
    private static final Color HEADER_GRAY = new Color(220, 220, 220);
    
    @SuppressWarnings("unused")
    private static final String[] DAYS_ARRAY = DAYS; // Pour éviter l'avertissement
    
    public static void exportTimetableToPDF(Map<String, Map<String, String>> classTimetable,
                                            Map<String, List<TimetableSolver.Course>> coursesByClass,
                                            String filename) throws DocumentException, IOException {
        
        Document document = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(document, new FileOutputStream(filename));
        document.open();
        
        // Titre
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph("EMPLOI DU TEMPS - DÉPARTEMENT INFORMATIQUE", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph(" "));
        
        // Date de génération
        Font dateFont = new Font(Font.HELVETICA, 10, Font.ITALIC);
        Paragraph date = new Paragraph("Généré le: " + new Date(), dateFont);
        date.setAlignment(Element.ALIGN_RIGHT);
        document.add(date);
        document.add(new Paragraph(" "));
        
        boolean firstClass = true;
        for (String className : classTimetable.keySet()) {
            // Nouvelle page pour chaque classe (sauf la première)
            if (!firstClass) {
                document.newPage();
            }
            firstClass = false;
            
            // Titre de la classe
            Font classFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Paragraph classTitle = new Paragraph("Classe: " + className, classFont);
            classTitle.setAlignment(Element.ALIGN_CENTER);
            classTitle.setSpacingAfter(10);
            document.add(classTitle);
            
            // Créer le tableau
            PdfPTable table = new PdfPTable(7); // 6 jours + 1 colonne pour les horaires
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setSpacingAfter(10);
            
            // Largeurs des colonnes
            float[] columnWidths = {1.5f, 2f, 2f, 2f, 2f, 2f, 2f};
            table.setWidths(columnWidths);
            
            // En-têtes
            Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            String[] headers = {"Horaire", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(HEADER_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(cell);
            }
            
            // Remplir le tableau
            Map<String, String> schedule = classTimetable.get(className);
            List<TimetableSolver.Course> classCourses = coursesByClass.get(className);
            
            for (int period = 0; period < 5; period++) {
                // Colonne horaire
                PdfPCell timeCell = new PdfPCell(new Phrase(PERIOD_LABELS[period] + "\n" + PERIODS[period]));
                timeCell.setBackgroundColor(LIGHT_GRAY);
                timeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                timeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(timeCell);
                
                for (int day = 0; day < 6; day++) {
                    String courseInfo = "";
                    boolean hasCourse = false;
                    
                    for (TimetableSolver.Course course : classCourses) {
                        String timeInfo = schedule.get(course.code);
                        if (timeInfo != null) {
                            String[] parts = timeInfo.split("\\|");
                            if (parts.length >= 2) {
                                try {
                                    int scheduledDay = Integer.parseInt(parts[0]);
                                    int scheduledPeriod = Integer.parseInt(parts[1]);
                                    String roomNum = parts.length > 2 ? parts[2] : "Salle?";
                                    
                                    if (scheduledDay == day && scheduledPeriod == period) {
                                        // Tronquer le nom si trop long
                                        String shortName = course.name.length() > 40 ? 
                                                          course.name.substring(0, 37) + "..." : course.name;
                                        courseInfo = shortName + "\n[" + course.code + "]\nSalle: " + roomNum;
                                        hasCourse = true;
                                        break;
                                    }
                                } catch (NumberFormatException e) {
                                    // Ignorer les erreurs de parsing
                                }
                            }
                        }
                    }
                    
                    if (!hasCourse) {
                        courseInfo = "LIBRE";
                    }
                    
                    PdfPCell cell = new PdfPCell(new Phrase(courseInfo));
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    
                    // Colorer les cours du matin
                    if (period < 2 && hasCourse) {
                        cell.setBackgroundColor(LIGHT_GREEN);
                    } else if (!hasCourse) {
                        cell.setBackgroundColor(VERY_LIGHT_GRAY);
                    }
                    
                    table.addCell(cell);
                }
            }
            
            document.add(table);
            
            // Statistiques de la classe
            int morningCount = 0;
            int totalCourses = classCourses.size();
            for (TimetableSolver.Course course : classCourses) {
                String timeInfo = schedule.get(course.code);
                if (timeInfo != null) {
                    String[] parts = timeInfo.split("\\|");
                    if (parts.length >= 2) {
                        try {
                            int period = Integer.parseInt(parts[1]);
                            if (period < 2) {
                                morningCount++;
                            }
                        } catch (NumberFormatException e) {
                            // Ignorer
                        }
                    }
                }
            }
            
            Font statsFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Paragraph stats = new Paragraph(String.format(
                "Statistiques: %d/%d cours avant midi (%.1f%%)", 
                morningCount, totalCourses, (totalCourses > 0 ? 100.0 * morningCount / totalCourses : 0)), statsFont);
            stats.setAlignment(Element.ALIGN_RIGHT);
            document.add(stats);
            document.add(new Paragraph(" "));
        }
        
        // Résumé global
        document.newPage();
        Font titleFont2 = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph summaryTitle = new Paragraph("RÉSUMÉ GLOBAL", titleFont2);
        summaryTitle.setAlignment(Element.ALIGN_CENTER);
        document.add(summaryTitle);
        document.add(new Paragraph(" "));
        
        PdfPTable summaryTable = new PdfPTable(5);
        summaryTable.setWidthPercentage(80);
        summaryTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        // En-têtes
        Font headerFont2 = new Font(Font.HELVETICA, 12, Font.BOLD);
        String[] summaryHeaders = {"Classe", "Total Cours", "Avant Midi", "Après Midi", "Taux"};
        for (String header : summaryHeaders) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont2));
            cell.setBackgroundColor(HEADER_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            summaryTable.addCell(cell);
        }
        
        int totalMorning = 0;
        int totalCourses = 0;
        
        for (String className : classTimetable.keySet()) {
            Map<String, String> schedule = classTimetable.get(className);
            List<TimetableSolver.Course> classCourses = coursesByClass.get(className);
            
            int morningCount = 0;
            for (TimetableSolver.Course course : classCourses) {
                String timeInfo = schedule.get(course.code);
                if (timeInfo != null) {
                    String[] parts = timeInfo.split("\\|");
                    if (parts.length >= 2) {
                        try {
                            int period = Integer.parseInt(parts[1]);
                            if (period < 2) {
                                morningCount++;
                            }
                        } catch (NumberFormatException e) {
                            // Ignorer
                        }
                    }
                }
            }
            
            totalMorning += morningCount;
            totalCourses += classCourses.size();
            int afternoonCount = classCourses.size() - morningCount;
            
            summaryTable.addCell(className);
            summaryTable.addCell(String.valueOf(classCourses.size()));
            summaryTable.addCell(String.valueOf(morningCount));
            summaryTable.addCell(String.valueOf(afternoonCount));
            summaryTable.addCell(String.format("%.1f%%", (classCourses.size() > 0 ? 100.0 * morningCount / classCourses.size() : 0)));
        }
        
        document.add(summaryTable);
        document.add(new Paragraph(" "));
        
        Font totalFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Paragraph total = new Paragraph(String.format(
            "TOTAL GÉNÉRAL: %d/%d cours avant midi (%.1f%%)", 
            totalMorning, totalCourses, (totalCourses > 0 ? 100.0 * totalMorning / totalCourses : 0)), totalFont);
        total.setAlignment(Element.ALIGN_CENTER);
        document.add(total);
        
        document.close();
        System.out.println("PDF exporté vers: " + filename);
    }
}