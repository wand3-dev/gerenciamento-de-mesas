# 🍞 AppMesas — Painel de Salão & Gestão Rápida de Pedidos

> **Ferramenta de alta performance para atendimento em padarias, cafeterias e restaurantes durante horários de pico.**

O **AppMesas** foi projetado para resolver o problema mais comum na operação de salão: **a lentidão de registrar pedidos duas vezes (no app e no sistema oficial da empresa)**. Com um **painel flutuante sobreposto**, comandos em 1 toque e busca instantânea, o atendimento é realizado em menos de 5 segundos diretamente na mesa do cliente.

---

## 🚀 Principais Recursos

### 1. 🪟 Modo Flutuante (Overlay sobre outros Apps)
- Uma bolha flutuante arrastável (`🍞 +PEDIDO`) fica sempre disponível no canto da tela do celular.
- **Opere sem alternar janelas:** você pode manter o sistema oficial da padaria ou o navegador aberto e, com um toque na bolha, abrir o painel compacto de anotações.
- Ao salvar, os dados são persistidos imediatamente e o painel se fecha sozinho, deixando tudo limpo para o próximo pedido.
- Ativação/desativação rápida pelo ícone **`🍞`** no topo da tela inicial.

### 2. ⚡ Chips dos Mais Pedidos (1 Toque)
- Atalhos rápidos com os produtos mais vendidos da padaria:
  - `Café Expresso`, `Café com Leite`, `Pão na Chapa`, `Pão de Queijo`, `Misto Quente`, `Suco de Laranja`, `Coxinha`, `Tapioca`.
- Um toque insere o item imediatamente na lista do pedido.

### 3. 📝 Observações Rápidas por Categoria
- Seletor inteligente com preparos frequentes organizados por segmento:
  - **☕ Cafés & Bebidas:** `Sem açúcar`, `Adoçante`, `Morno`, `Bem quente`, `Puro`, `Pingado`, `Sem gelo`...
  - **🥖 Pães & Lanches:** `Bem tostado`, `Pouca manteiga`, `Sem manteiga`, `Queijo bem derretido`, `Cortar ao meio`...
  - **🥤 Sucos & Vitaminas:** `Sem açúcar`, `Com leite`, `Com água`, `Sem gelo`, `Bem gelado`...
  - **🥟 Salgados:** `Bem quente`, `Para viagem`, `Ketchup/Mostarda`...
- Clicar na observação anexa o detalhe diretamente ao produto selecionado.

### 4. 🔍 Busca Instantânea com Nome Completo (Word-Wrap)
- Busca reativa a partir de **1 caractere** por **nome** ou **código**.
- Catálogo filtrado e limpo com mais de 6.500 produtos reais de panificação e conveniência.
- Nomes longos quebram a linha automaticamente sem cortes com reticências (`...`).

### 5. ⏱️ Gestão Visual de Mesas e Tempo de Espera
- Painel de 1 a 34 mesas com status em tempo real (`LIVRE` / `ABERTA`).
- Contadores de pedidos **pendentes** e **entregues**.
- Alertas visuais com graduação de cores conforme o tempo de espera do pedido:
  - 🟢 **Recente:** verde (< 15 min).
  - 🟠 **Atenção:** âmbar (>= 15 min).
  - 🔴 **Urgente:** vermelho (>= 30 min).

### 6. 🔄 Sincronização em Tempo Real (Event-Driven)
- Pedidos salvos via painel flutuante atualizam instantaneamente a tela principal e a tela de detalhe da mesa via `BroadcastReceiver`, sem necessidade de recarregar a tela.

---

## 📱 Telas do Aplicativo

| Tela | Descrição |
| :--- | :--- |
| **Painel de Salão (`MainActivity`)** | Grid completo das 34 mesas, resumo de abertas/fechadas e botão superior para ativar a bolha flutuante. |
| **Detalhes da Mesa (`MesaDetailActivity`)** | Linha do tempo de pedidos, status de entrega, troca/transferência de comanda entre mesas e botão de anotação rápida. |
| **Painel Flutuante (`FloatingWidgetService`)** | Mini dashboard overlay para ser usado sobre outros aplicativos com favoritos, busca, obs e salvar instantâneo. |

---

## 🛠️ Stack Tecnológica

- **Linguagem:** Java 8+
- **Plataforma:** Android Nativo (SDK 24 a 34)
- **Interface:** Material Design Components, ConstraintLayout, CoordinatorLayout, RecyclerView
- **Serviços em Background:** `Service` de Overlay com `WindowManager` (`SYSTEM_ALERT_WINDOW`)
- **Comunicação Interna:** Broadcasts locais para reatividade em tempo real
- **Persistência de Dados:** SharedPreferences com estrutura serializada robusta (offline-first)

---

## 📥 Instalação

### Pré-requisitos
- Dispositivo Android 7.0 (Nougat) ou superior.
- Permissão de **"Sobrepor a outros aplicativos"** (necessária apenas para o modo de bolha flutuante).

### Via ADB (Desenvolvimento)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.garcom.appmesas/.MainActivity
```

### Compilação do Código Fonte
```bash
gradle assembleDebug
```
O APK gerado estará em `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🔒 Permissões Utilizadas

- `SYSTEM_ALERT_WINDOW`: Permite exibir a bolha flutuante e o mini painel sobreposto a qualquer outro app ou navegador.
- `FOREGROUND_SERVICE`: Mantém o widget ativo e responsivo em segundo plano durante o turno de trabalho.
- `RECORD_AUDIO` / Voz: Utilizado pelo reconhecimento de voz nativo do Google Speech Recognizer para ditado de pedidos.

---

## 👤 Autor & Suporte

Desenvolvido por **wand**  
- **WhatsApp:** [(85) 99989-3711](https://wa.me/5585999893711)  
- **Instagram:** [@_wand3_](https://www.instagram.com/_wand3_/)  
- **Fortaleza, CE - Brasil**
