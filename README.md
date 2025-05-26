# IscTorrent

Sistema P2P de partilha de ficheiros desenvolvido em Java.

## Execução

```bash
java pt.iscte.pcd.isctorrent.Main <porta> <diretório_trabalho>
```

Exemplo:
```bash
java pt.iscte.pcd.isctorrent.Main 8081 dl1
```

## Funcionalidades

- Arquitetura peer-to-peer sem servidor central
- Interface gráfica para pesquisa e downloads
- Download distribuído em blocos de múltiplos nós
- Coordenação usando mecanismos próprios de sincronização

## Como usar

1. **Conectar a nós**: Use o botão "Conectar" para ligar a outros nós
2. **Pesquisar ficheiros**: Introduza palavra-chave e clique "Procurar"
3. **Download**: Selecione ficheiros da lista e clique "Transferir"

## Estrutura do projeto

- `core/` - Classes principais do sistema
- `download/` - Gestão de downloads distribuídos
- `network/` - Comunicação entre nós
- `gui/` - Interface gráfica
- `protocol/` - Mensagens de comunicação
- `sync/` - Coordenação personalizada

## Requisitos

- Java 17 ou superior
- Apenas bibliotecas padrão do Java