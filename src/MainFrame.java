import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

public class MainFrame {
    private JFrame frame;
    private static final String DIR_PATH = "Text";
    private final int liczbaWyrazowStatystyki;
    private final AtomicBoolean fajrant;
    private final int liczbaProducentow;
    private final int liczbaKonsumentow;
    private ExecutorService executor;
    private List<Future<?>> producentFuture;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e){
            e.printStackTrace();
        }
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try{
                    MainFrame window = new MainFrame();
                    window.frame.pack();
                    window.frame.setAlwaysOnTop(true);
                    window.frame.setVisible(true);
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
    }
    public MainFrame(){
        liczbaWyrazowStatystyki = 10;
        fajrant = new AtomicBoolean(false);
        liczbaKonsumentow = 2;
        liczbaProducentow = 1;
        executor = Executors.newFixedThreadPool(liczbaProducentow + liczbaKonsumentow);
        producentFuture = new ArrayList<>();
        initilize();
    }

    private void initilize(){
        frame = new JFrame();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                executor.shutdown();
            }
        });
        frame.setBounds(100, 100, 450, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        frame.getContentPane().add(panel, BorderLayout.NORTH);
        JButton btnStop = new JButton("Stop");
        btnStop.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fajrant.set(true);
                for(Future<?> f : producentFuture){
                    f.cancel(true);
                }
            }
        });
        JButton btnStart = new JButton("Start");
        btnStart.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                multiThreadedStatistics();
            }
        });
        JButton btnZamknij = new JButton("Zamknij");
        btnZamknij.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executor.shutdown();
                System.exit(0);
            }
        });
        panel.add(btnStart);
        panel.add(btnStop);
        panel.add(btnZamknij);
    }

    private void multiThreadedStatistics(){
        for(Future<?> f : producentFuture){
            if(!f.isDone()){
                JOptionPane.showMessageDialog(frame, "Nie można uruchomić nowego zadania! " +
                        "Przynajmniej jeden producent nadal pracuje!", "OSTRZEŻENIE", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        fajrant.set(false);
        producentFuture.clear();
        final BlockingQueue<Optional<Path>> kolejka = new LinkedBlockingQueue<>(liczbaKonsumentow);
        final int przerwa = 60;

        Runnable producent = () ->{
            final String name = Thread.currentThread().getName();
            String info = String.format("Producent %s uruchomiony ...", name);
            System.out.println(info);

            while(!Thread.currentThread().isInterrupted()){
                Path lookPath = FileSystems.getDefault().getPath(DIR_PATH);
                if(fajrant.get()) {
                    for (int i = 0; i < liczbaKonsumentow; i++) {
                        kolejka.add(Optional.ofNullable(null));
                    }
                    break;
                } else {
                    try {
                        Files.walkFileTree(lookPath, new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                if (file.toString().endsWith(".txt")) {
                                    Optional<Path> filePath = Optional.of(file.toRealPath());
                                    try {
                                        kolejka.put(filePath);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });


                    } catch (IOException e) {
                        e.printStackTrace();

                    }
                }
                info = String.format("Producent %s ponownie sprawdzi katalogi za %d sekund", name, przerwa);
                System.out.println(info);
                try {
                    TimeUnit.SECONDS.sleep(przerwa);
                } catch (InterruptedException e) {
                    info = String.format("Przerwa producenta %s przerwana!", name);
                    System.out.println(info);
                    if(!fajrant.get())
                        Thread.currentThread().interrupt();
                }
            }
            info = String.format("PRODUCENT %s SKOŃCZYŁ PRACĘ", name);
            System.out.println(info);
        };
        Runnable konsument = () -> {
            final String name = Thread.currentThread().getName();
            String info = String.format("KONSUMENT %s URUCHOMIONY ...", name);
            System.out.println(info);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Optional<Path> filePath = kolejka.take();
                    if (filePath.isPresent()) {
                        try {
                            System.out.println(getLinkedCountedWords(filePath.get(), 20));

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        break;
                    }
                } catch (InterruptedException e) {
                    info = String.format("Oczekiwanie konsumenta %s na nowy element z kolejki przerwane!", name);
                    System.out.println(info);
                    Thread.currentThread().interrupt();
                }
            }
            info = String.format("KONSUMENT %s ZAKOŃCZYŁ PRACĘ", name);
            System.out.println(info);
        };

        for (int i = 0; i < liczbaProducentow; i++) {
            Future<?> pf = executor.submit(producent);
            producentFuture.add(pf);
        }

        for (int i = 0; i < liczbaKonsumentow; i++) {
            executor.execute(konsument);
        }
    }


    private Map<String, Long> getLinkedCountedWords(Path path, int wordsLimit) throws IOException {
        //konstrukcja 'try-with-resources' - z automatycznym zamykaniem strumienia/źródła danych
        try (BufferedReader reader = Files.newBufferedReader(path)) {// wersja ze wskazaniem kodowania
            // Files.newBufferedReader(path, StandardCharsets.UTF_8)
            return reader.lines()
                    .flatMap(line -> Arrays.stream(line.split(" ")))
                    .filter(word -> word.matches("[a-zA-Z]{3,}"))
                    .map(String::toLowerCase)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(wordsLimit)
                    .collect(Collectors.toMap( //umieszczenie elementów strumienia w mapie zachowującej kolejność tj. LinkedHashMap
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (k,v) -> { throw new IllegalStateException(String.format("Błąd! Duplikat klucza %s.", k)); },
                            LinkedHashMap::new));
        }
    }
}
