import java.io.File;
import java.io.FileOutputStream;
import java.util.Scanner;
import java.util.List;
import java.awt.Color;
import java.util.logging.Logger;

public class DosSend {
    final int FECH = 44100; // fréquence d'échantillonnage
    final int FP = 1000; // fréquence de la porteuses
    final int BAUDS = 100; // débit en symboles par seconde
    final int FMT = 16; // format des données
    final int MAX_AMP = (1 << (FMT - 1)) - 1; // AMPLITUDE max en entier
    final int CHANNELS = 1; // nombre de voies audio (1 = mono)
    final int[] START_SEQ = { 1, 0, 1, 0, 1, 0, 1, 0 }; // séquence de synchro au début
    final Scanner input = new Scanner(System.in); // pour lire le fichier texte

    private static final Color[] COLORS = { StdDraw.BLUE, StdDraw.RED, StdDraw.GREEN, StdDraw.YELLOW, StdDraw.ORANGE }; // Tableau
    public static final String MODEINCONNU = "mode inconnu"; // Mode inconnu
    public static final String MODELINE = "line"; // Mode line
    public static final String MODEPOINT = "point"; // Mode point
    public static final double FREQUENCE = 0.02; // Permet de plus ou moins voir la sinusoidale
    public static final double AMPLITUDE = 0.9; // AMPLITUDE de la sinusoidale <= 1

    private static boolean isDrawingSinusoidal = false; // Dessiner ou non sinusoidale
    long taille; // nombre d'octets de données à transmettre
    double duree; // durée de l'audio
    double[] dataMod; // données modulées
    char[] dataChar; // données en char
    FileOutputStream outStream; // flux de sortie pour le fichier .wav

    // Créez un logger pour votre classe
    private static final Logger logger = Logger.getLogger(DosSend.class.getName());

    /**
     * Constructor
     * 
     * @param path the path of the wav file to create
     */
    public DosSend(String path) {
        File file = new File(path);
        try {
            outStream = new FileOutputStream(file);
        } catch (Exception e) {
            System.out.println("Erreur de création du fichier");
        }
    }

    /**
     * Write a raw 4-byte integer in little endian
     * 
     * @param octets     the integer to write
     * @param destStream the stream to write in
     */
    public void writeLittleEndian(int octets, int taille, FileOutputStream destStream) {
        char poidsFaible;
        while (taille > 0) {
            poidsFaible = (char) (octets & 0xFF);
            try {
                destStream.write(poidsFaible);
            } catch (Exception e) {
                System.out.println("Erreur d'écriture");
            }
            octets = octets >> 8;
            taille--;
        }
    }

    /**
     * Create and write the header of a wav file
     *
     */
    public void writeWavHeader() {
        taille = (long) (FECH * duree);
        long nbBytes = taille * CHANNELS * FMT / 8;

        try {
            // [Bloc de déclaration d'un fichier au format WAVE]
            // FileTypeBlocID
            outStream.write(new byte[] { 'R', 'I', 'F', 'F' });
            // FileSize
            writeLittleEndian((int) (nbBytes + 36), 4, outStream);
            // FileFormatID
            outStream.write(new byte[] { 'W', 'A', 'V', 'E' });
            // [Bloc décrivant le format audio]
            // FormatBlocID
            outStream.write(new byte[] { 'f', 'm', 't', ' ' });
            // BlocSize
            writeLittleEndian(16, 4, outStream);
            // AudioFormat (2 octets) : Format du stockage dans le fichier (1: PCM entier,
            // 3: PCM flottant, 65534: étendu)
            writeLittleEndian(1, 2, outStream); // PCM entier
            // NumChannels (2 octets) : Nombre de canaux (1 pour mono, 2 pour stéréo, etc.)
            writeLittleEndian(CHANNELS, 2, outStream);
            // SampleRate (4 octets) : Fréquence d'échantillonnage (en Hz)
            writeLittleEndian(FECH, 4, outStream);
            // ByteRate (4 octets) : Débit binaire (nombre d'octets par seconde)
            writeLittleEndian(FECH * CHANNELS * FMT / 8, 4, outStream);
            // BlockAlign (2 octets) : Nombre d'octets pour un échantillon, tous canaux
            // confondus
            writeLittleEndian(CHANNELS * FMT / 8, 2, outStream);
            // BitsPerSample (2 octets) : Bits par échantillon (par canal)
            writeLittleEndian(FMT, 2, outStream);
            // [Bloc des données]
            // DataBlocID
            outStream.write(new byte[] { 'd', 'a', 't', 'a' });
            // DataSize (4 octets) : Taille des données audio (en octets)
            writeLittleEndian((int) nbBytes, 4, outStream);

        } catch (Exception e) {
            e.toString();
        }
    }

    /**
     * Write the data in the wav file
     * after normalizing its AMPLITUDE to the maximum value of the format (8 bits
     * signed)
     */
    public void writeNormalizeWavData() {
        try {
            for (int i = 0; i < dataMod.length; i++) {
                double sample = dataMod[i];
                // Normalisation des échantillons entre -1 et 1
                double normalizedSample = sample / MAX_AMP;

                // Quantification de l'échantillon dans la plage du format PCM 16 bits
                int quantizedSample = (int) (normalizedSample * MAX_AMP);

                // Écriture des échantillons normalisés dans le fichier .wav
                writeLittleEndian(quantizedSample, FMT / 8, outStream);
            }
        } catch (Exception e) {
            System.out.println("Erreur d'écriture");
        }
    }

    /**
     * Read the text data to encode and store them into dataChar
     * 
     * @return the number of characters read
     */
    public int readTextData() {
        String fullText = ""; // Pour créer une chaîne de caractères à partir du fichier texte
        while (input.hasNextLine()) { // Lis chaque ligne du fichier texte
            String ligne = input.nextLine(); // Récupère une ligne
            fullText += ligne; // Concatène les lignes dans une grande chaîne de caractères
        }
        dataChar = fullText.toCharArray(); // Crée un tableau de char de la taille de la chaîne de caractères

        return dataChar.length; // Renvoie la taille du tableau de char soit le nombre de caractères du fichier
                                // texte
    }

    /**
     * convert a char array to a bit array
     * 
     * @param chars
     * @return byte array containing only 0 & 1
     */
    public byte[] charToBits(char[] chars) {
        byte[] result = new byte[chars.length * 8]; // Chaque char est sur 8 bits (1 octets)

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            int index = i * 8; // Index dans le tableau résultant
            for (int j = 7; j >= 0; j--) { // Parcours de chaque bit dans le char
                result[index + j] = (byte) (c & 1); // Extraction du bit de poids faible
                c >>= 1; // Décalage du char d'un bit vers la droite
            }
        }

        return result;
    }

    /**
     * Modulate the data to send and apply the symbol throughput via BAUDS and FECH.
     * 
     * @param bits the data to modulate
     */
    public void modulateData(byte[] bits) {
        double dureeSymbole = 1.0 / BAUDS; // Durée d'un symbole en secondes
        int echantillonsParSymbole = (int) (dureeSymbole * FECH); // Nombre d'échantillons par symbole

        dataMod = new double[bits.length * echantillonsParSymbole + START_SEQ.length]; // + START_SEQ.length pour le
                                                                                       // préfixe

        // Ajout du préfixe
        for (int i = 0; i < START_SEQ.length; i++) { // Parcours du préfixe donné
            dataMod[i] = START_SEQ[i] * (double) FP;
        }

        // Modulation des données
        for (int i = START_SEQ.length; i < bits.length * echantillonsParSymbole + START_SEQ.length; i++) {
            int bitIndex = (i - START_SEQ.length) / echantillonsParSymbole;
            dataMod[i] = bits[bitIndex] * (double) FP;
        }
    }

    /**
     * Display a signal in a window
     * 
     * @param sig   the signal to display
     * @param start the first sample to display
     * @param stop  the last sample to display
     * @param mode  "line" or "point"
     * @param title the title of the window
     */
    public static void displaySig(int[] sig, int start, int stop, String mode, String title) {
        initializeCanvas(start, stop, title, mode);
        for (int i = start; i < stop - 1; i++) {
            double x1 = i;
            double x2 = i + 1.0;
            // Change le mode de dessin en fonction de la valeur de sig
            if (sig[i] != 0 && !isDrawingSinusoidal) {
                isDrawingSinusoidal = true;
            }
            // Dessine en fonction du mode
            dessinSignal(mode, x1, x2);
        }
    }

    /**
     * Display a signal in a window
     * 
     * @param sig   the signal to display
     * @param start the first sample to display
     * @param stop  the last sample to display
     * @param mode  "line" or "point"
     * @param title the title of the window
     */
    public static void displaySig(double[] sig, int start, int stop, String mode, String title) {
        initializeCanvas(start, stop, title, mode);
        for (int i = start; i < stop - 1; i++) {
            double x1 = i;
            double x2 = i + 1.0;
            // Change le mode de dessin en fonction de la valeur de sig
            if (sig[i] != 0 && !isDrawingSinusoidal) {
                isDrawingSinusoidal = true;
            }
            // Dessine en fonction du mode
            dessinSignal(mode, x1, x2);
        }
    }

    /**
     * Display signals in a window
     * 
     * @param listOfSigs a list of the signals to display
     * @param start      the first sample to display
     * @param stop       the last sample to display
     * @param mode       "line" or "point"
     * @param title      the title of the window
     */
    public static void displaySig(List<double[]> listOfSigs, int start, int stop, String mode, String title) {

        initializeCanvas(start, stop, title, mode);

        for (int j = 0; j < listOfSigs.size(); j++) {
            changeCouleur(j % COLORS.length); // Change la couleur du signal en fonction de son index
            for (int i = start; i < stop - 1; i++) {
                double x1 = i;
                double x2 = i + 1.0;
                // Change le mode de dessin en fonction de la valeur de sig
                if (listOfSigs.get(j)[i] != 0 && !isDrawingSinusoidal) {
                    isDrawingSinusoidal = true;
                }
                // Dessine en fonction du mode
                dessinSignal(mode, x1, x2);
            }
        }

    }

    /**
     * Dessine en fonction du mode
     * 
     * @param mode mode de dessin
     * @param x1   abscisse de départ
     * @param x2   abscisse de fin
     */
    public static void dessinSignal(String mode, double x1, double x2) {
        // Dessine en fonction du mode
        if (mode.equals(MODELINE)) {
            dessinLine(x1, x2);
        } else {
            dessinPoint(x1, x2);
        }
    }

    /**
     * Change la couleur du signal en fonction de son index
     * 
     * @param index index du signal
     */
    public static void changeCouleur(int index) {
        StdDraw.setPenColor(COLORS[index]); // Change la couleur du signal en fonction de son index
    }

    /**
     * Dessine une sinusoidale
     * 
     * @param x1        abscisse de départ
     * @param x2        abscisse de fin
     * @param AMPLITUDE AMPLITUDE de la sinusoidale
     * @param FREQUENCE fréquence de la sinusoidale
     */
    public static void dessinSinusoidaleLine(double x1, double x2) {
        // dessiner la sinusoidale
        for (double t = x1; t < x2; t += 0.2) {
            double y = AMPLITUDE * Math.sin(2 * Math.PI * FREQUENCE * t); // Calcul de l'ordonnée
            StdDraw.line(t, y, t + 0.2, AMPLITUDE * Math.sin(2 * Math.PI * FREQUENCE * (t + 0.2))); // Dessine
            // la
            // sinusoidale
        }
    }

    /**
     * Dessine une sinusoidale
     * 
     * @param x1        abscisse de départ
     * @param x2        abscisse de fin
     * @param AMPLITUDE AMPLITUDE de la sinusoidale
     * @param FREQUENCE fréquence de la sinusoidale
     */
    public static void dessinSinusoidalePoint(double x1, double x2) {
        // dessiner la sinusoidale
        for (double t = x1; t < x2; t += 0.1) {
            double y = AMPLITUDE * Math.sin(2 * Math.PI * FREQUENCE * t); // Calcul de l'ordonnée
            StdDraw.point(t, y); // Dessine le point de la sinusoidale
            // la
            // sinusoidale
        }
    }

    /**
     * Dessine une sinusoidale ou une ligne droite
     * 
     * @param x1 abscisse de départ
     * @param x2 abscisse de fin
     */
    public static void dessinLine(double x1, double x2) {
        // Dessine soit une sinusoidale soit une ligne droite
        if (isDrawingSinusoidal) {

            dessinSinusoidaleLine(x1, x2);

            // Une fois la sinusoidale dessinée, réinitialise le mode à false
            isDrawingSinusoidal = false;
        } else {
            // Dessine une ligne droite au milieu Y
            StdDraw.line(x1, 0, x2, 0);
        }
    }

    /*
     * Dessine une sinusoidale ou une ligne droite
     * 
     * @param x1 abscisse de départ
     * 
     * @param x2 abscisse de fin
     */
    public static void dessinPoint(double x1, double x2) {
        // Dessine soit une sinusoidale soit une ligne droite
        if (isDrawingSinusoidal) {

            dessinSinusoidalePoint(x1, x2);

            // Une fois la sinusoidale dessinée, réinitialise le mode à false
            isDrawingSinusoidal = false;
        } else {
            // Dessine une ligne droite au milieu
            StdDraw.point(x1, 0);
        }
    }

    /**
     * Affiche un message d'erreur
     * 
     * @param message message d'erreur
     */
    public static void printError(String message) {
        logger.warning(message);
        System.exit(1); // Quitte le programme 1 pour erreur
    }

    /**
     * Initialize the canvas
     * 
     * @param start the first sample to display
     * @param stop  the last sample to display
     * @param title the title of the window
     */
    public static void initializeCanvas(int start, int stop, String title, String mode) {
        if (start > stop) { // Vérifie que start est inférieur à stop
            printError("start doit être inférieur à stop");
        }

        if (!mode.equals(MODELINE) && !mode.equals(MODEPOINT)) { // Si le mode n'est pas line ou point
            printError(MODEINCONNU);
        }

        StdDraw.setCanvasSize(800, 400); // Définit la taille de la fenêtre
        StdDraw.setXscale(start, stop); // Définit l'échelle des abscisses
        StdDraw.setYscale(-1, 1); // Définit l'échelle des ordonnées
        StdDraw.setTitle(title); // Définit le titre de la fenêtre
        StdDraw.setPenColor(StdDraw.BLACK);
        StdDraw.line(start, 0, stop, 0); // Dessine l'axe des abscisses
        StdDraw.text(start + 50, 0.9, String.valueOf(0.9)); // Affiche la hauteur de la porteuse
        StdDraw.text(start + 50, -0.9, String.valueOf(-0.9));
        // Dessine la barre graduée
        for (int i = start; i < stop + 200; i += 200) {
            StdDraw.line(i, -0.02, i, 0.02);
            StdDraw.text(i, -0.1, String.valueOf(i));
        }
        StdDraw.setPenColor(StdDraw.BLUE);
    }

    public static void main(String[] args) {
        // créé un objet DosSend
        DosSend dosSend = new DosSend("DosOok_message.wav");
        // lit le texte à envoyer depuis l'entrée standard
        // et calcule la durée de l'audio correspondant
        dosSend.duree = (double) (dosSend.readTextData() + dosSend.START_SEQ.length / 8.0) * 8.0 / dosSend.BAUDS;

        // génère le signal modulé après avoir converti les données en bits
        dosSend.modulateData(dosSend.charToBits(dosSend.dataChar));
        // écrit l'entête du fichier wav
        dosSend.writeWavHeader();
        // écrit les données audio dans le fichier wav
        dosSend.writeNormalizeWavData();

        // affiche les caractéristiques du signal dans la console
        System.out.println("Message : " + String.valueOf(dosSend.dataChar));
        System.out.println("\tNombre de symboles : " + dosSend.dataChar.length);
        System.out.println("\tNombre d'échantillons : " + dosSend.dataMod.length);
        System.out.println("\tDurée : " + dosSend.duree + " s");
        System.out.println();

        // exemple d'affichage du signal modulé dans une fenêtre graphique
        displaySig(dosSend.dataMod, 1000, 3000, "line", "Signal modulé");
    }
}
