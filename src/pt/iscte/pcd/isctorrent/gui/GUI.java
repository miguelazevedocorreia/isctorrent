package pt.iscte.pcd.isctorrent.gui;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.gui.dialogs.ConnectionDialog;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Interface gráfica principal do IscTorrent
 * Permite pesquisar, transferir ficheiros e conectar a nós
 */
public class GUI extends JFrame {
    private final IscTorrent torrent;
    private final JTextField searchField;
    private final JList<FileItem> resultsList;
    private final DefaultListModel<FileItem> resultsModel;
    private final JButton searchButton;
    private final JButton downloadButton;
    private final JButton connectButton;

    /**
     * Item da lista para agrupar ficheiros iguais
     */
    private static class FileItem {
        final String fileName;
        final long fileSize;
        final Set<String> sources;
        final List<FileSearchResult> results;

        FileItem(FileSearchResult result) {
            this.fileName = result.getFileName();
            this.fileSize = result.getFileSize();
            this.sources = new HashSet<>();
            this.results = new ArrayList<>();
            addResult(result);
        }

        void addResult(FileSearchResult result) {
            sources.add(result.getNodeAddress() + ":" + result.getNodePort());
            results.add(result);
        }

        @Override
        public String toString() {
            return String.format("%s (%d bytes) - %d nó%s",
                    fileName,
                    fileSize,
                    sources.size(),
                    sources.size() > 1 ? "s" : ""
            );
        }
    }

    /**
     * Construtor da interface
     * @param torrent Instância principal do IscTorrent
     * @param port Porta do nó local
     */
    public GUI(IscTorrent torrent, int port) {
        super("IscTorrent - Porta: " + port);
        this.torrent = torrent;

        // Configurar janela
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 500);
        setMinimumSize(new Dimension(600, 400));

        // Painel principal
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Painel de pesquisa
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Pesquisa"));

        searchField = new JTextField();
        searchField.setToolTipText("Insira palavras-chave para pesquisar ficheiros");

        searchButton = new JButton("Procurar");
        searchButton.setToolTipText("Pesquisar ficheiros na rede");

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        // Lista de resultados
        resultsModel = new DefaultListModel<>();
        resultsList = new JList<>(resultsModel);
        resultsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultsList.setCellRenderer(new FileItemRenderer());

        JScrollPane scrollPane = new JScrollPane(resultsList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Resultados"));

        // Painel de botões
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        downloadButton = new JButton("Transferir");
        downloadButton.setToolTipText("Transferir ficheiros selecionados");
        downloadButton.setEnabled(false);

        connectButton = new JButton("Conectar");
        connectButton.setToolTipText("Conectar a um novo nó");

        buttonPanel.add(downloadButton);
        buttonPanel.add(connectButton);

        // Adicionar componentes
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.EAST);

        // Barra de estado
        JLabel statusBar = new JLabel("Pronto");
        statusBar.setBorder(BorderFactory.createLoweredBevelBorder());
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Configurar eventos
        searchButton.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch());
        downloadButton.addActionListener(e -> downloadSelected());
        connectButton.addActionListener(e -> showConnectionDialog());

        resultsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                downloadButton.setEnabled(!resultsList.isSelectionEmpty());
            }
        });

        // Centrar janela
        setLocationRelativeTo(null);
        setVisible(true);

        // Focar campo de pesquisa
        searchField.requestFocusInWindow();
    }

    /**
     * Renderizador customizado para os items da lista
     */
    private static class FileItemRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof FileItem item) {
                setText(item.toString());
                setToolTipText(String.format("Fontes: %s",
                        String.join(", ", item.sources)));
            }

            return this;
        }
    }

    /**
     * Executa uma pesquisa
     */
    private void performSearch() {
        String keyword = searchField.getText().trim();

        if (keyword.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Por favor insira uma palavra-chave",
                    "Pesquisa Vazia",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Limpar resultados anteriores
        resultsModel.clear();

        // Desabilitar controles durante pesquisa
        searchButton.setEnabled(false);
        searchField.setEnabled(false);

        // Executar pesquisa em thread separada
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                torrent.searchFiles(keyword);
                return null;
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                searchField.setEnabled(true);
                searchField.selectAll();
                searchField.requestFocusInWindow();
            }
        };

        worker.execute();
    }

    /**
     * Transfere ficheiros selecionados
     */
    private void downloadSelected() {
        List<FileItem> selectedItems = resultsList.getSelectedValuesList();

        if (selectedItems.isEmpty()) {
            return;
        }

        // Iniciar downloads
        for (FileItem item : selectedItems) {
            // Usar o primeiro resultado disponível
            if (!item.results.isEmpty()) {
                FileSearchResult result = item.results.get(0);
                torrent.startDownload(result);
            }
        }

        // Feedback ao utilizador
        String message = selectedItems.size() == 1
                ? "A iniciar transferência de 1 ficheiro"
                : "A iniciar transferência de " + selectedItems.size() + " ficheiros";

        JOptionPane.showMessageDialog(this,
                message,
                "Transferência Iniciada",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Mostra diálogo de conexão
     */
    private void showConnectionDialog() {
        ConnectionDialog.ConnectionResult result = ConnectionDialog.showDialog(this);

        if (result != null) {
            torrent.connectToNode(result.address(), result.port());
        }
    }

    /**
     * Exibe resultados de pesquisa
     * @param results Lista de resultados
     */
    public void displaySearchResults(List<FileSearchResult> results) {
        SwingUtilities.invokeLater(() -> {
            resultsModel.clear();

            // Agrupar resultados por ficheiro
            Map<String, FileItem> fileMap = new HashMap<>();

            for (FileSearchResult result : results) {
                String key = result.getFileName() + "-" + result.getFileSize();

                FileItem item = fileMap.get(key);
                if (item == null) {
                    item = new FileItem(result);
                    fileMap.put(key, item);
                } else {
                    item.addResult(result);
                }
            }

            // Adicionar à lista
            for (FileItem item : fileMap.values()) {
                resultsModel.addElement(item);
            }

            // Atualizar interface
            if (resultsModel.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Nenhum ficheiro encontrado",
                        "Sem Resultados",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    /**
     * Adiciona resultados incrementalmente
     * @param results Novos resultados
     */
    public void addSearchResults(List<FileSearchResult> results) {
        // Por enquanto, apenas exibe todos os resultados
        displaySearchResults(results);
    }
}