package com.timetable;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CSVExporter {
    
    private static final String[] DAYS = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"};
    private static final String[] PERIODS = {"07:00-09:55", "10:05-12:55", "13:05-15:55", "16:05-18:55", "19:05-21:55"};
    
    // Définir les en-têtes comme constantes
    private static final String[] HEADERS_TIMETABLE = {
        "Classe", "Jour", "Période", "Horaire", "Cours", "Code", "Salle", "Statut"
    };
    
    private static final String[] HEADERS_SUMMARY = {
        "Classe", "Total Cours", "Cours Avant Midi", "Cours Après Midi", "Taux (%)"
    };
    
    public static void exportTimetableToCSV(Map<String, Map<String, String>> classTimetable,
                                             Map<String, List<TimetableSolver.Course>> coursesByClass,
                                             String filename) throws IOException {
        
        // Utiliser builder() au lieu de withHeader() deprecated
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(HEADERS_TIMETABLE)
                .build();
        
        try (FileWriter out = new FileWriter(filename);
             CSVPrinter printer = new CSVPrinter(out, format)) {
            
            for (String className : classTimetable.keySet()) {
                Map<String, String> schedule = classTimetable.get(className);
                List<TimetableSolver.Course> classCourses = coursesByClass.get(className);
                
                for (int day = 0; day < 6; day++) {
                    for (int period = 0; period < 5; period++) {
                        boolean hasCourse = false;
                        for (TimetableSolver.Course course : classCourses) {
                            String timeInfo = schedule.get(course.code);
                            if (timeInfo != null) {
                                String[] parts = timeInfo.split("\\|");
                                if (parts.length >= 2) {
                                    try {
                                        int scheduledDay = Integer.parseInt(parts[0]);
                                        int scheduledPeriod = Integer.parseInt(parts[1]);
                                        
                                        if (scheduledDay == day && scheduledPeriod == period) {
                                            String roomNum = parts.length > 2 ? parts[2] : "N/A";
                                            String status = (period < 2) ? "AVANT MIDI" : "APRÈS MIDI";
                                            
                                            printer.printRecord(
                                                className,
                                                DAYS[day],
                                                period + 1,
                                                PERIODS[period],
                                                course.name,
                                                course.code,
                                                roomNum,
                                                status
                                            );
                                            hasCourse = true;
                                        }
                                    } catch (NumberFormatException e) {
                                        // Ignorer
                                    }
                                }
                            }
                        }
                        if (!hasCourse) {
                            printer.printRecord(className, DAYS[day], period + 1, PERIODS[period], 
                                              "LIBRE", "-", "-", "-");
                        }
                    }
                }
            }
            
            System.out.println("CSV détaillé exporté vers: " + filename);
        }
    }
    
    public static void exportSummaryToCSV(Map<String, Map<String, String>> classTimetable,
                                          Map<String, List<TimetableSolver.Course>> coursesByClass,
                                          String filename) throws IOException {
        
        // Utiliser builder() au lieu de withHeader() deprecated
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(HEADERS_SUMMARY)
                .build();
        
        try (FileWriter out = new FileWriter(filename);
             CSVPrinter printer = new CSVPrinter(out, format)) {
            
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
                
                int total = classCourses.size();
                int afternoonCount = total - morningCount;
                double rate = total > 0 ? (100.0 * morningCount / total) : 0;
                
                printer.printRecord(className, total, morningCount, afternoonCount, 
                                   String.format("%.1f", rate));
            }
            
            System.out.println("Résumé CSV exporté vers: " + filename);
        }
    }
}