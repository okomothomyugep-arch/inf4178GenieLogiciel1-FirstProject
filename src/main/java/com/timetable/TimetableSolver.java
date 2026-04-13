package com.timetable;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;

public class TimetableSolver {
    
    private static final int NB_DAYS = 6;
    private static final int NB_PERIODS = 5;
    private static final int MORNING_CUTOFF = 2;
    
    public static void solve() throws Exception {
        Loader.loadNativeLibraries();
        
        List<Room> rooms = loadRooms("rooms.json");
        List<Course> courses = loadCourses("subjects.json");
        
        System.out.println("=== DONNÉES CHARGÉES ===");
        System.out.println("Salles: " + rooms.size());
        System.out.println("Cours: " + courses.size());
        
        // Grouper par classe
        Map<String, List<Course>> coursesByClass = new HashMap<>();
        for (Course c : courses) {
            coursesByClass.computeIfAbsent(c.className, k -> new ArrayList<>()).add(c);
        }
        
        System.out.println("\n=== PLANIFICATION PAR CLASSE ===");
        
        // Planifier chaque classe indépendamment
        Map<String, Map<String, String>> classTimetable = new HashMap<>();
        
        for (Map.Entry<String, List<Course>> entry : coursesByClass.entrySet()) {
            String className = entry.getKey();
            List<Course> classCourses = entry.getValue();
            
            System.out.println("\nPlanification de " + className + " (" + classCourses.size() + " cours)");
            
            Map<String, String> schedule = scheduleClass(className, classCourses, rooms);
            
            if (schedule != null) {
                classTimetable.put(className, schedule);
                System.out.println("    Planifié avec succès");
            } else {
                System.out.println("    Tentative avec algorithme glouton...");
                schedule = greedySchedule(className, classCourses, rooms);
                if (schedule != null) {
                    classTimetable.put(className, schedule);
                    System.out.println("    Planifié avec succès (glouton)");
                } else {
                    System.out.println("    Échec de planification");
                }
            }
        }
        
        // Afficher l'emploi du temps combiné
        System.out.println("\n" + "=".repeat(60));
        System.out.println("EMPLOI DU TEMPS GÉNÉRÉ");
        System.out.println("=".repeat(60));
        printCombinedTimetable(classTimetable, coursesByClass);

       exportResults(classTimetable, coursesByClass);
    }
    
    // =========================================================================
    // MÉTHODE DE PLANIFICATION AVEC OR-TOOLS
    // =========================================================================
    private static Map<String, String> scheduleClass(String className, List<Course> courses, List<Room> rooms) {
        CpModel model = new CpModel();
        
        // Variables: x[cours][jour][periode][salle]
        Map<String, IntVar> x = new HashMap<>();
        
        for (Course course : courses) {
            for (int day = 0; day < NB_DAYS; day++) {
                for (int period = 0; period < NB_PERIODS; period++) {
                    for (Room room : rooms) {
                        String key = String.format("%s|%s|%d|%d|%s", 
                            course.code, className, day, period, room.num);
                        x.put(key, model.newBoolVar(key));
                    }
                }
            }
        }
        
        // ---------------------------------------------------------------------
        // CONTRAINTE N°2 : Chaque cours doit être programmé exactement une fois par semaine
        // ---------------------------------------------------------------------
        for (Course course : courses) {
            List<IntVar> courseVars = new ArrayList<>();
            for (int day = 0; day < NB_DAYS; day++) {
                for (int period = 0; period < NB_PERIODS; period++) {
                    for (Room room : rooms) {
                        String key = String.format("%s|%s|%d|%d|%s", 
                            course.code, className, day, period, room.num);
                        courseVars.add(x.get(key));
                    }
                }
            }
            model.addEquality(LinearExpr.sum(courseVars.toArray(new IntVar[0])), 1);
        }
        
        // ---------------------------------------------------------------------
        // CONTRAINTE N°1 : Une classe ne peut pas avoir deux cours différents
        // à la même période le même jour
        // ---------------------------------------------------------------------
        for (int day = 0; day < NB_DAYS; day++) {
            for (int period = 0; period < NB_PERIODS; period++) {
                List<IntVar> periodVars = new ArrayList<>();
                for (Course course : courses) {
                    for (Room room : rooms) {
                        String key = String.format("%s|%s|%d|%d|%s", 
                            course.code, className, day, period, room.num);
                        periodVars.add(x.get(key));
                    }
                }
                model.addLessOrEqual(LinearExpr.sum(periodVars.toArray(new IntVar[0])), 1);
            }
        }
        
        // ---------------------------------------------------------------------
        // CONTRAINTE N°4 (Objectif) : Maximiser le nombre de cours avant midi
        // (périodes 1 et 2)
        // ---------------------------------------------------------------------
        List<IntVar> morningVars = new ArrayList<>();
        for (Course course : courses) {
            for (int day = 0; day < NB_DAYS; day++) {
                for (int period = 0; period < MORNING_CUTOFF; period++) {
                    for (Room room : rooms) {
                        String key = String.format("%s|%s|%d|%d|%s", 
                            course.code, className, day, period, room.num);
                        morningVars.add(x.get(key));
                    }
                }
            }
        }
        model.maximize(LinearExpr.sum(morningVars.toArray(new IntVar[0])));
        
        // Résolution
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(20.0);
        CpSolverStatus status = solver.solve(model);
        
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            Map<String, String> schedule = new HashMap<>();
            for (Course course : courses) {
                for (int day = 0; day < NB_DAYS; day++) {
                    for (int period = 0; period < NB_PERIODS; period++) {
                        for (Room room : rooms) {
                            String key = String.format("%s|%s|%d|%d|%s", 
                                course.code, className, day, period, room.num);
                            if (solver.value(x.get(key)) == 1) {
                                schedule.put(course.code, day + "|" + period + "|" + room.num);
                            }
                        }
                    }
                }
            }
            return schedule;
        }
        
        return null;
    }
    
    
    private static Map<String, String> greedySchedule(String className, List<Course> courses, List<Room> rooms) {
        Map<String, String> schedule = new HashMap<>();
        boolean[][] used = new boolean[NB_DAYS][NB_PERIODS];
        
        // Trier les cours par priorité (optionnel)
        List<Course> sortedCourses = new ArrayList<>(courses);
        
        for (Course course : sortedCourses) {
            boolean scheduled = false;
            
            // Essayer d'abord les créneaux du matin
            for (int period = 0; period < MORNING_CUTOFF && !scheduled; period++) {
                for (int day = 0; day < NB_DAYS && !scheduled; day++) {
                    if (!used[day][period]) {
                        // Prendre la première salle disponible
                        String roomNum = rooms.get(0).num;
                        schedule.put(course.code, day + "|" + period + "|" + roomNum);
                        used[day][period] = true;
                        scheduled = true;
                    }
                }
            }
            
            // Si pas trouvé le matin, essayer l'après-midi
            for (int period = MORNING_CUTOFF; period < NB_PERIODS && !scheduled; period++) {
                for (int day = 0; day < NB_DAYS && !scheduled; day++) {
                    if (!used[day][period]) {
                        String roomNum = rooms.get(0).num;
                        schedule.put(course.code, day + "|" + period + "|" + roomNum);
                        used[day][period] = true;
                        scheduled = true;
                    }
                }
            }
            
            if (!scheduled) {
                System.out.println("      Impossible de placer: " + course.name);
                return null;
            }
        }
        
        return schedule;
    }
    
    private static void printCombinedTimetable(Map<String, Map<String, String>> classTimetable,
                                                Map<String, List<Course>> coursesByClass) {
        String[] dayNames = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"};
        String[] periodTimes = {"07:00-09:55", "10:05-12:55", "13:05-15:55", "16:05-18:55", "19:05-21:55"};
        
        int totalMorningCourses = 0;
        int totalCourses = 0;
        
        for (String className : classTimetable.keySet()) {
            Map<String, String> schedule = classTimetable.get(className);
            List<Course> classCourses = coursesByClass.get(className);
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("" + className);
            System.out.println("=".repeat(60));
            
            int classMorningCount = 0;
            
            for (int day = 0; day < NB_DAYS; day++) {
                System.out.println("\n " + dayNames[day]);
                System.out.println("-".repeat(50));
                
                for (int period = 0; period < NB_PERIODS; period++) {
                    String icon = (period < MORNING_CUTOFF) ? "" : "";
                    System.out.printf("\n  %s Période %d (%s)\n",icon, period + 1, periodTimes[period]);
                    System.out.println("  " + "-".repeat(40));
                    
                    boolean found = false;
                    for (Course course : classCourses) {
                        String timeInfo = schedule.get(course.code);
                        if (timeInfo != null) {
                            String[] parts = timeInfo.split("\\|");
                            int scheduledDay = Integer.parseInt(parts[0]);
                            int scheduledPeriod = Integer.parseInt(parts[1]);
                            String roomNum = parts.length > 2 ? parts[2] : "Salle?";
                            
                            if (scheduledDay == day && scheduledPeriod == period) {
                                System.out.printf("      %s\n", course.name);
                                System.out.printf("       Code: %s | Salle: %s\n", course.code, roomNum);
                                found = true;
                                if (period < MORNING_CUTOFF) {
                                    classMorningCount++;
                                    totalMorningCourses++;
                                }
                                totalCourses++;
                            }
                        }
                    }
                    if (!found) {
                        System.out.println("     (libre)");
                    }
                }
            }
            
            System.out.printf("\n %s: %d/%d cours avant midi (%.1f%%)\n", 
                className, classMorningCount, classCourses.size(),
                100.0 * classMorningCount / classCourses.size());
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("RÉSUMÉ GLOBAL");
        System.out.println("=".repeat(60));
        System.out.printf("Total cours: %d\n", totalCourses);
        System.out.printf("Cours avant midi: %d\n", totalMorningCourses);
        if (totalCourses > 0) {
            System.out.printf("Taux global: %.1f%%\n", 100.0 * totalMorningCourses / totalCourses);
        }
        System.out.println("=".repeat(60));
    }
    
    // =========================================================================
    // CHARGEMENT DES DONNÉES JSON
    // =========================================================================
    private static List<Room> loadRooms(String filename) throws Exception {
        List<Room> rooms = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        File file = new File(filename);
        
        if (!file.exists()) {
            file = new File("src/main/resources/" + filename);
        }
        
        JsonNode root = mapper.readTree(file);
        JsonNode infoNode = root.get("Informatique");
        
        if (infoNode != null && infoNode.isArray()) {
            for (JsonNode node : infoNode) {
                Room room = new Room();
                room.num = node.get("num").asText();
                room.capacite = Integer.parseInt(node.get("capacite").asText());
                room.batiment = node.get("batiment").asText();
                rooms.add(room);
            }
        }
        return rooms;
    }
    
    private static List<Course> loadCourses(String filename) throws Exception {
        List<Course> courses = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        File file = new File(filename);
        
        if (!file.exists()) {
            file = new File("src/main/resources/" + filename);
        }
        
        JsonNode root = mapper.readTree(file);
        JsonNode niveau = root.get("niveau");
        
        if (niveau != null) {
            for (Iterator<String> it = niveau.fieldNames(); it.hasNext();) {
                String niveauStr = it.next();
                int niveauInt = Integer.parseInt(niveauStr);
                JsonNode semestres = niveau.get(niveauStr);
                
                for (String sem : new String[]{"s1", "s2"}) {
                    if (semestres.has(sem)) {
                        JsonNode subjects = semestres.get(sem).get("subjects");
                        if (subjects != null && subjects.isArray()) {
                            for (JsonNode sub : subjects) {
                                Course course = new Course();
                                course.className = "INFO" + niveauInt + "_" + sem.toUpperCase();
                                
                                JsonNode nameNode = sub.get("name");
                                if (nameNode != null && !nameNode.isArray()) {
                                    course.name = nameNode.asText();
                                } else if (nameNode != null && nameNode.isArray() && nameNode.size() > 0) {
                                    course.name = nameNode.get(0).asText();
                                } else {
                                    course.name = "";
                                }
                                
                                course.code = sub.has("code") ? sub.get("code").asText() : "";
                                course.credit = sub.has("credit") ? sub.get("credit").asInt() : 0;
                                
                                
                                // =========================================================================
                                // CONTRAINTE N°3 : Un cours ne peut être programmé que si la classe le suit
                                // =========================================================================

                                if (course.name != null && !course.name.isEmpty() && 
                                    !course.name.equals("null") && 
                                    course.credit > 0) {
                                    courses.add(course);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        System.out.println("Cours chargés: " + courses.size());
        return courses;
    }
    
    static class Room {
        String num;
        int capacite;
        String batiment;
    }
    
    static class Course {
        String className;
        String name;
        String code;
        int credit;
    }

   // À ajouter à la fin de la classe TimetableSolver
public static void exportResults(Map<String, Map<String, String>> classTimetable,
                                  Map<String, List<Course>> coursesByClass) {
    try {
        // Créer le dossier d'export s'il n'existe pas
        File exportDir = new File("exports");
        if (!exportDir.exists()) {
            exportDir.mkdir();
        }
        
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        
        // Exporter en CSV
        String csvFile = "exports/timetable_" + timestamp + ".csv";
        String summaryCsvFile = "exports/summary_" + timestamp + ".csv";
        String pdfFile = "exports/timetable_" + timestamp + ".pdf";
        
        CSVExporter.exportTimetableToCSV(classTimetable, coursesByClass, csvFile);
        CSVExporter.exportSummaryToCSV(classTimetable, coursesByClass, summaryCsvFile);
        PDFExporter.exportTimetableToPDF(classTimetable, coursesByClass, pdfFile);
        
        System.out.println("\n Fichiers exportés dans le dossier 'exports/'");
        System.out.println("   - " + csvFile);
        System.out.println("   - " + summaryCsvFile);
        System.out.println("   - " + pdfFile);
        
    } catch (Exception e) {
        System.err.println("Erreur lors de l'export: " + e.getMessage());
        e.printStackTrace();
    }
}
}