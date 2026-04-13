package com.timetable;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== GÉNÉRATEUR D'EMPLOI DU TEMPS ===\n");
        
        try {
            // Résoudre l'emploi du temps
            TimetableSolver.solve();
            
            // Note: Les exports sont maintenant automatiques dans TimetableSolver
            System.out.println("\n Génération terminée avec succès !");
            
        } catch (Exception e) {
            System.err.println("Erreur: " + e.getMessage());
            e.printStackTrace();
        }
    }
}