import java.util.logging.Logger;
import java.io.*;

public class DosRead {
    static final int FP = 1000;
    static final int BAUDS = 100;
    static final int[] START_SEQ = { 1, 0, 1, 0, 1, 0, 1, 0 };
    FileInputStream fileInputStream;
    int sampleRate = 44100;
    int bitsPerSample;
    int dataSize;
    double[] audio;
    int[] outputBits;
    char[] decodedChars;
    public static final String MODEINCONNU = "mode inconnu"; // Mode inconnu
    public static final String MODELINE = "line"; // Mode line
    public static final String MODEPOINT = "point"; // Mode point
    public static final double FREQUENCE = 0.02; // Permet de plus ou moins voir la sinusoidale
    public static final double AMPLITUDE = 0.9; // AMPLITUDE de la sinusoidale <= 1

    private static boolean isDrawingSinusoidal = false; // Dessiner ou non sinusoidale

    // Créez un logger pour votre classe
    private static final Logger logger = Logger.getLogger(DosSend.class.getName());

    /**
     * Constructor that opens the FIlEInputStream
     * and reads sampleRate, bitsPerSample and dataSize
     * from the header of the wav file
     * 
     * @param path the path of the wav file to read
     */
    public void readWavHeader(String path) {
        byte[] header = new byte[44]; // The header is 44 bytes long
        try {
            fileInputStream = new FileInputStream(path);
            fileInputStream.read(header);

            // Extraction des informations du header
            sampleRate = byteArrayToInt(header, 24, 32); // Taux d'échantillonnage
            bitsPerSample = byteArrayToInt(header, 34, 16); // Bits par échantillon
            dataSize = byteArrayToInt(header, 40, 32); // Taille des données

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper method to convert a little-endian byte array to an integer
     * 
     * @param bytes  the byte array to convert
     * @param offset the offset in the byte array
     * @param fmt    the format of the integer (16 or 32 bits)
     * @return the integer value
     */
    private static int byteArrayToInt(byte[] bytes, int offset, int fmt) {
        if (fmt == 16)
            return ((bytes[offset + 1] & 0xFF) << 8) | (bytes[offset] & 0xFF);
        else if (fmt == 32)
            return ((bytes[offset + 3] & 0xFF) << 24) |
                    ((bytes[offset + 2] & 0xFF) << 16) |
                    ((bytes[offset + 1] & 0xFF) << 8) |
                    (bytes[offset] & 0xFF);
        else
            return (bytes[offset] & 0xFF);
    }

    /**
     * Read the audio data from the wav file
     * and convert it to an array of doubles
     * that becomes the audio attribute
     */
    public void readAudioDouble() {
        byte[] audioData = new byte[dataSize];
        try {
            fileInputStream.read(audioData);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Crée un tableau de doubles pour stocker les données audio converties
        audio = new double[audioData.length / 2];
        // Convertit les données audio de bytes en doubles
        for (int i = 0; i < audio.length; i++) { // parcourt le tableau audio
            audio[i] = (double) ((audioData[2 * i] & 0xFF) | ((audioData[2 * i + 1]) << 8)); // Convertit les données
            // audio de bytes en doubles
        }

    }

    /**
     * Reverse the negative values of the audio array
     */

    public void audioRectifier() {
        // Vérifie si le tableau audio est vide ou null
        if (audio == null || audio.length == 0) {
            System.out.println("Aucune donnée audio à rectifier.");
            return;
        }
        // Parcourt le tableau audio pour rectifier les valeurs négatives
        for (int i = 0; i < audio.length; i++) {
            if (audio[i] < 0) {
                audio[i] = -audio[i]; // Remplace les valeurs négatives par leur valeur absolue (positive)
            }
        }
    }

    /**
     * Apply a low pass filter to the audio array
     * Fc = (1/2n)*FECH
     * 
     * @param n the number of samples to average
     */
    public void audioLPFilter(int n) {
        // Vérifie si le tableau audio est vide ou null
        if (audio == null || audio.length == 0) {
            System.out.println("Aucune donnée audio à filtrer.");
            return;
        }

        double[] filteredAudio = new double[audio.length];

        // Applique le filtre passe-bas en utilisant une moyenne sur n échantillons
        for (int i = 0; i < audio.length; i++) {
            double sum = 0;
            int count = 0;

            // Calcule la moyenne des échantillons sur n valeurs avec une fenêtre glissante
            for (int j = Math.max(0, i - n + 1); j <= Math.min(audio.length - 1, i + n - 1); j++) {
                sum += audio[j];
                count++;
            }

            filteredAudio[i] = sum / count; // Stocke la moyenne dans le tableau filtré
        }

    }

    /**
     * Resample the audio array and apply a threshold
     * 
     * @param period    the number of audio samples by symbol
     * @param threshold the threshold that separates 0 and 1
     */
    public void audioResampleAndThreshold(int period, int threshold) {
        // Vérifie si le tableau audio est vide ou null
        if (audio == null || audio.length == 0) {
            System.out.println("Aucune donnée audio à rééchantillonner et à seuiller.");
            return;
        }

        // Rééchantillonnage du tableau audio
        double[] resampledAudio = new double[audio.length / period];
        for (int i = 0; i < resampledAudio.length; i++) {
            double sum = 0;

            // Calcule la somme des échantillons sur la période donnée
            for (int j = i * period; j < (i + 1) * period; j++) {
                sum += audio[j] * 100.0; // Multiplie par 100 pour augmenter l'amplitude sinon ça n'y arrive pas
            }

            // Stocke la moyenne des échantillons de la période dans le tableau
            // rééchantillonné
            resampledAudio[i] = sum / period;
        }

        // Applique le seuil au tableau rééchantillonné
        for (int i = 0; i < resampledAudio.length; i++) {
            if (resampledAudio[i] >= threshold) {
                resampledAudio[i] = 1; // Valeur haute (1)
            } else {
                resampledAudio[i] = 0; // Valeur basse (0)
            }
        }

        // Remplace le tableau audio original par le tableau rééchantillonné seuillé
        audio = resampledAudio;

        outputBits = new int[audio.length]; // Crée un tableau de bits pour stocker les bits de sortie

        for (int i = 0; i < audio.length; i++) { // Parcours le tableau audio
            outputBits[i] = (int) audio[i]; // Convertit les données audio de doubles en bits
        }
    }

    /**
     * Decode the outputBits array to a char array
     * The decoding is done by comparing the START_SEQ with the actual beginning of
     * outputBits.
     * The next first symbol is the first bit of the first char.
     */

    public void decodeBitsToChar() {
        if (outputBits == null || outputBits.length == 0) {
            System.out.println("Aucune donnée de sortie à décoder.");
            return;
        }

        StringBuilder decodedString = new StringBuilder();
        int startIndex = 0;

        // Recherche de la séquence START_SEQ dans outputBits
        for (int i = 0; i < outputBits.length - START_SEQ.length; i++) {
            boolean matchFound = true;
            for (int j = 0; j < START_SEQ.length; j++) {
                if (outputBits[i + j] != START_SEQ[j]) {
                    matchFound = false;
                    break;
                }
            }
            if (matchFound) {
                startIndex = i + START_SEQ.length;
                break;
            }
        }

        // Lecture des bits après la séquence START_SEQ pour former les caractères
        for (int i = startIndex; i < outputBits.length; i += 8) {
            int byteVal = 0;
            for (int j = 0; j < 8; j++) {
                byteVal = (byteVal << 1) | outputBits[i + j];
            }
            decodedString.append((char) byteVal);
        }

        decodedChars = decodedString.toString().toCharArray();
    }

    /**
     * Print the elements of an array
     * 
     * @param data the array to print
     */
    public static void printIntArray(char[] data) {
        for (int i = 0; i < data.length; i++) { // Parcours le tableau
            System.out.print(data[i]); // Affiche l'élément à l'index i
        }
        System.out.println(""); // Saut de ligne
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

    /**
     * Un exemple de main qui doit pourvoir être exécuté avec les méthodes
     * que vous aurez conçues.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java DosRead <input_wav_file>");
            return;
        }
        String wavFilePath = args[0];

        // Open the WAV file and read its header
        DosRead dosRead = new DosRead();
        dosRead.readWavHeader(wavFilePath);

        // Print the audio data properties
        System.out.println("Fichier audio: " + wavFilePath);
        System.out.println("\tSample Rate: " + dosRead.sampleRate + " Hz");
        System.out.println("\tBits per Sample: " + dosRead.bitsPerSample + " bits");
        System.out.println("\tData Size: " + dosRead.dataSize + " bytes");

        // Read the audio data
        dosRead.readAudioDouble();
        // reverse the negative values
        dosRead.audioRectifier();
        // apply a low pass filter
        dosRead.audioLPFilter(44);
        // Resample audio data and apply a threshold to output only 0 & 1
        dosRead.audioResampleAndThreshold(dosRead.sampleRate / BAUDS, 12000);

        dosRead.decodeBitsToChar();
        if (dosRead.decodedChars != null) {
            System.out.print("Message décodé : ");
            printIntArray(dosRead.decodedChars);
        }

        displaySig(dosRead.audio, 0, dosRead.audio.length - 1, "line", "Signal audio");

        // Close the file input stream
        try {
            dosRead.fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
