# Plugin Nyris - Minas Personalizadas

**Autor:** Morreu  
**Versão:** Minecraft 1.21.1  

## 📜 Descrição
O **plugin** permite que jogadores criem minas personalizadas temporárias, com blocos de minério configuráveis, sistema de expiração automática, regeneração e proteção contra exploits. Ideal para eventos ou mecânicas especiais em servidores.

---

## ⚡ Funcionalidades

### Comandos
- `/nyris` – Recebe o item especial para criação de minas.
- `/nyris limpar` – Remove todas as minas ativas.

### Sistema de Minas
- **Cubo Perfeito**: Dimensões lado × lado × altura, totalmente configuráveis.
- **Criação Direta**: Clique com botão direito para criar a mina onde está olhando.
- **Posicionamento de Borda**: Mina criada a partir do ponto de clique (não centralizada).
- **Spawn de Blocos**: Gera blocos de minério definidos na configuração.
- **Sistema de Expiração**: Tempo de duração configurável.
- **Regeneração Automática**: Mundo restaurado ao estado anterior.
- **Efeitos de Criação**: Sons, títulos e partículas configuráveis.
- **Minérios Aleatórios**: Usa lista definida no `config.yml`.
- **Proteção Exclusiva**: Apenas o criador pode minerar.
- **Persistência via MySQL**: Minas continuam após reinício do servidor.
- **Cooldown e Limite**: Configuráveis para evitar spam e abuso.
- **Segurança**: Proteção contra lag, duplicações e acesso indevido.

---

## 📦 Instalação
1. Baixe o arquivo `.jar` do plugin.
2. Coloque-o na pasta `plugins` do seu servidor.
3. Reinicie o servidor.
4. O plugin será carregado automaticamente.

---

## ⚙️ Configuração

### `config.yml` (exemplo)
mine:
  dimensions:
    side: 10      # Lado do cubo (largura e profundidade)
    height: 5     # Altura do cubo
  
  duration:
    active_time: 3600    # Tempo ativo em segundos

  blocks:
    - DIAMOND_ORE
    - GOLD_ORE
    - IRON_ORE

  cooldown: 600          # Tempo de espera entre criações
  max_mines_per_player: 1

mysql:
  host: "localhost"
  port: 3306
  database: "nyris"
  username: "root"
  password: ""
