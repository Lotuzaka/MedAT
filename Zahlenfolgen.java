import java.util.ArrayList;
import java.util.Arrays;

public class Zahlenfolgen {
    private static int generateRandom(int intV, boolean neg) {
        int x = (int)(Math.random()* intV * (Math.random() > 0.33 ? 1 : (!neg ? 1 : -1)));
        return x==0 ? 1 : x;
    }

    private static ArrayList<Integer> folge = new ArrayList<>(9);
    private static ArrayList<Integer> ergebnis = new ArrayList<>(2);
    private static ArrayList<String> erg = new ArrayList<String>(100);

    // System 1, 1er-Sprung: R1, R2, R1, R2
    public static void EinserSprung(int erste) {
        int op1 = generateRandom(30, true);
        int op2 = generateRandom(30, true);
        int next = 0;

        folge.add(erste);
        folge.add(erste + op1);

        for (int i = 1; i < 8; i++) {
            if ((i % 2) == 0) next = folge.get(i) + op1;
            else next = folge.get(i) + op2;
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 2, 1er-Sprung: R1, R2, R1+x, R2+y
    private static void EinserSprung2(int erste) {
        int op1 = generateRandom(30, true);
        int op2 = generateRandom(30, true);
        int op3 = generateRandom(10, true);
        int op4 = generateRandom(10, true);
        int next = 0;

        folge.add(erste);
        folge.add(erste + op1);

        //System.out.println("Operators: " + op1 + ", " + op2 + ", " + op3 + ", " + op4);

        for (int i = 1; i < 8; i++) {
            if ((i%2)==0) {
                op1 = op1+op4;
                next = folge.get(i) + op1;
            }
            else {
                next = folge.get(i) + op2;
                op2 = op2+op3;
            }
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 3, 1er-Sprung 2 Ebenen: R+0x, R+1x, R+3x, R+7x
    private static void EinserSprung2Ebenen(int erste) {
        int op1 = generateRandom(30, false);
        int op2 = generateRandom(10, false);
        int op3 = generateRandom(4, false);
        int next = 0;

        folge.add(erste);
        folge.add(erste + op1);

        //System.out.println("Operators: " + op1 + ", " + op2 + ", " + op3);

        for (int i = 1; i < 8; i++) {
            op1 = op1 + op2;
            next = folge.get(i) + op1;
            op2 = op2*op3;
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 4, 1er-Sprung: R1, R2, R3
    private static void EinserCombo(int erste) {
        int op1 = generateRandom(6, false);
        int op2 = generateRandom(6, false);
        int op3 = generateRandom(30, true);
        int next = 0;

        folge.add(erste);

        // System.out.println("Operators: " + op1 + ", " + op2 + ", " + op3);

        for (int i = 0; i < 8; i++) {
            next = folge.get(i);
            if (i%3 == 0) next = next - op1;
            if (i%3 == 1) next = next * op2;
            if (i%3 == 2) next = next + op3;

            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 5, 1er-Sprung: R1, R2, R3
    private static void EinserSprung3(int erste) {
        int op1 = generateRandom(20, true);
        int op2 = generateRandom(20, true);
        int op3 = generateRandom(3, false);
        int op4 = generateRandom(4, false);

        int next = 0;

        folge.add(erste);

        //System.out.println("Operators: " + op1 + ", " + op2 + ", " + op3 + ", " + op4);

        for (int i = 0; i < 8; i++) {
            next = folge.get(i);
            if (i%3 == 0) {
                next = next + op1;
                op1 += op4;
            }
            if (i%3 == 1) {
                next = next + op2;
                op2 += op4;
            }
            if (i%3 == 2) {
                next = next * op3;
                op3 += op4;
            }
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 6, Fibonacci: R1+R2 = R3
    private static void Fibonacci(int erste, int zweite) {

        int next;

        folge.add(erste);
        folge.add(zweite);

        for (int i = 1; i < 8; i++) {
            next = folge.get(i) + folge.get(i-1);
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 7, FibonacciAlt: R1+R2 = R3, R3-R2 = R4
    private static void FibonacciAlt(int erste, int zweite) {

        int next = 0;

        folge.add(erste);
        folge.add(zweite);

        for (int i = 1; i < 8; i++) {
            if (i%2==1) next = folge.get(i) + folge.get(i-1);
            if (i%2==0) next = folge.get(i) - folge.get(i-1);
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 8, Fibonacci3: R1+R2+R3 = R4
    private static void Fibonacci3(int erste, int zweite, int dritte) {

        int next;

        folge.add(erste);
        folge.add(zweite);
        folge.add(dritte);

        for (int i = 2; i < 8; i++) {
            next = folge.get(i) + folge.get(i-1) + folge.get(i-2);
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 9, FibonacciSprung: R1+R3 = R4, R2+R4 = R5
    private static void FibonacciSprung(int erste, int zweite, int dritte) {

        int next;

        folge.add(erste);
        folge.add(zweite);
        folge.add(dritte);

        for (int i = 2; i < 8; i++) {
            next = folge.get(i) + folge.get(i-2);
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 10, EinserFibonacci: R's: R1+R2 = R3
    private static void EinserFibonacci(int erste) {

        int next;
        int op1 = generateRandom(10,false);
        int op2 = generateRandom(10,false);
        int op3;

        folge.add(erste);

        for (int i = 0; i < 8; i++) {
            if (i==0) next = folge.get(i) + op1;
            else if (i==1) next = folge.get(i) + op2;
            else {
                next = folge.get(i) + op1 + op2;
                op3 = op2;
                op2 = op1 + op3;
                op1 = op3;
            }
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 11, ZweierSprung
    private static void ZweierSprung(int erste, int zweite) {

        int next=0;
        int op1 = generateRandom(30,true);
        int op2 = generateRandom(30,true);

        folge.add(erste);
        folge.add(zweite);

        for (int i = 1; i < 8; i++) {
            if (i%2==1) next = folge.get(i-1) + op1;
            if (i%2==0) next = folge.get(i-1) + op2;
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 12, ZweierSprungAlt
    private static void ZweierSprungAlt(int erste, int zweite) {

        int next=0;
        int op1 = generateRandom(30,true);
        int op2 = generateRandom(12,true);
        int op3 = generateRandom(6,false);

        folge.add(erste);
        folge.add(zweite);

        for (int i = 1; i < 8; i++) {
            if (i%2==1) next = folge.get(i-1) + op1;
            if (i%2==0) next = folge.get(i-1) + op2;
            op1 += op3;
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

     // System 13, DreierSprung
     private static void DreierSprung (int erste, int zweite, int dritte) {
         int next;
         int op1 = generateRandom(8,false);

         folge.add(erste);
         folge.add(zweite);
         folge.add(dritte);

         // System.out.println(op1);

         for (int i = 2; i < 8; i++) {
             next = (i%2==0 ? folge.get(i-2) + op1 : folge.get(i-2) * op1);
             folge.add(next);
             if (i > 5) ergebnis.add(next);
         }
     }

    // System 14, DreierSprungAufst
    private static void DreierSprungAufst (int erste, int zweite, int dritte) {
        int next;
        int op1 = generateRandom(3,false);
        int op2 = generateRandom(3,false);

        folge.add(erste);
        folge.add(zweite);
        folge.add(dritte);

        for (int i = 2; i < 8; i++) {
            next = folge.get(i-2) * op1;
            op1 += op2;
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 15, ZweierSprungAlt2
    private static void ZweierSprungAlt2(int erste, int zweite) {

        int next=0;
        int op1 = generateRandom(30,true);
        int op2 = generateRandom(30,true);
        int op3 = generateRandom(4,false);

        folge.add(erste);
        folge.add(zweite);

        for (int i = 1; i < 8; i++) {
            if (i%2==1) {
                next = folge.get(i-1) + op1;
                op1 *= op3;
            }
            if (i%2==0) {
                next = folge.get(i-1) + op2;
                op2 *= op3;
            }

            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 16, 1er-Sprung 3 Ebenen
    private static void EinserSprung3Ebenen(int erste) {
        int op1 = generateRandom(30, true);
        int op2 = generateRandom(10, false);
        int op3 = generateRandom(4, false);
        int op4 = generateRandom(4, false);
        int next = 0;

        folge.add(erste);
        folge.add(erste + op1);

        //System.out.println("Operators: " + op1 + ", " + op2 + ", " + op3);

        for (int i = 1; i < 8; i++) {
            op1 = op1 + op2;
            next = folge.get(i) + op1;
            op2 += op3;
            op3 += op4;
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    // System 17, 1er-Sprung Ziffernquersumme
    private static void Ziffernquersumme(int erste) {
        int next = 0;

        folge.add(erste);

        //System.out.println("Operators: " + op1 + ", " + op2 + ", " + op3);

        for (int i = 0; i < 8; i++) {
            next = folge.get(i) + getQuersumme(folge.get(i));
            folge.add(next);
            if (i > 5) ergebnis.add(next);
        }
    }

    private static int getQuersumme(int i){

        String zahl = ""+i;
        int erg = 0;

        for(int a = 0; a < zahl.length(); a++){
            erg = erg + Integer.parseInt(String.valueOf(zahl.charAt(a)));
        }
        return erg;
    }

    private static void AntwortAusgabe (){
        folge.remove(folge.size()-1);
        folge.remove(folge.size()-1);

        for (int number: folge) {
            System.out.print(number + "  ");
        }
        System.out.println("? ?");

        int zuweisen = (int)(Math.random() * 100);
        int erg1 = ergebnis.get(0);
        int erg2 = ergebnis.get(1);
        if (zuweisen > 60) {
            if (zuweisen > 80) {
                System.out.println("A) " + erg1 + "/" + erg2);
                System.out.println("B) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
                erg.add("A");
            } else {
                System.out.println("A) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
                System.out.println("B) " + erg1 + "/" + erg2);
                erg.add("B");
            }
            System.out.println("C) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
            System.out.println("D) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
            System.out.println("E) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));

        } else if (zuweisen > 20){
            System.out.println("A) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
            System.out.println("B) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
            if (zuweisen > 40) {
                System.out.println("C) " + erg1 + "/" + erg2);
                System.out.println("D) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
                erg.add("C");
            } else {
                System.out.println("C) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
                System.out.println("D) " + erg1 + "/" + erg2);
                erg.add("D");
            }
            System.out.println("E) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
        } else {
            System.out.println("A) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
            System.out.println("B) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
            System.out.println("C) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
            System.out.println("D) " + (erg1+generateRandom(15,true)) + "/" + (erg2+generateRandom(15,true)));
            System.out.println("E) " + erg1 + "/" + erg2);
            erg.add("E");
        }
        System.out.println();
    }

    public static void main(String[] args) {
        int first;
        int second;
        int third;
        int zufall;

        System.out.println("---------- EASY ----------");
        for (int easy = 0; easy < 20; easy++) {
            System.out.println(easy+1 + ")");

            first = generateRandom(50,false);
            second = generateRandom(50,false);
            third = generateRandom(50,false);
            zufall = (int)(Math.random());

            ergebnis.clear();
            folge.clear();

            if (zufall < 0.2) EinserSprung(first);
            if (zufall >= 0.2 && zufall < 0.4) EinserSprung2(first);
            if (zufall >= 0.4 && zufall < 0.6) Fibonacci(first,second);
            if (zufall >= 0.6 && zufall < 0.8) ZweierSprung(first, second);
            if (zufall >= 0.8 && zufall < 1) DreierSprung(first,second,third);

            AntwortAusgabe();
        }
        System.out.println("---------- MEDIUM ----------");
        for (int medium = 0; medium < 40; medium++) {
            System.out.println(medium+1 + ")");

            first = generateRandom(50,false);
            second = generateRandom(50,false);
            third = generateRandom(50,false);
            zufall = (int)(Math.random());

            ergebnis.clear();
            folge.clear();

            if (zufall < 0.167) EinserSprung2Ebenen(first);
            if (zufall >= 0.167 && zufall < 0.33) EinserCombo(first);
            if (zufall >= 0.33 && zufall < 0.5) FibonacciAlt(first,second);
            if (zufall >= 0.5 && zufall < 0.66) Fibonacci3(first,second,third);
            if (zufall >= 0.66 && zufall < 0.83) EinserFibonacci(first);
            if (zufall >= 0.83 && zufall < 1) ZweierSprungAlt2(first, second);

            AntwortAusgabe();
        }
        System.out.println("---------- HARD ----------");
        for (int hard = 0; hard < 40; hard++) {
            System.out.println(hard+1 + ")");

            first = generateRandom(50,false);
            second = generateRandom(50,false);
            third = generateRandom(50,false);
            zufall = (int)(Math.random());

            ergebnis.clear();
            folge.clear();

            if (zufall < 0.167) EinserSprung3(first);
            if (zufall >= 0.167 && zufall < 0.33) FibonacciSprung(first,second,third);
            if (zufall >= 0.33 && zufall < 0.5) ZweierSprungAlt(first, second);
            if (zufall >= 0.5 && zufall < 0.66) DreierSprungAufst(first,second,third);
            if (zufall >= 0.66 && zufall < 0.83) EinserSprung3Ebenen(first);
            if (zufall >= 0.83 && zufall < 1) Ziffernquersumme(first);

            AntwortAusgabe();
        }

        System.out.println("---------- LÖSUNGEN ----------");
        int i = 1;
        for (String x: erg) {
            System.out.println(i + ") " + x);
            i++;
        }

    }
}
