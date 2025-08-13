# Plugin Nyris - Minas Personalizadas

## Descrição
O Plugin Nyris permite que jogadores criem minas personalizadas no Minecraft 1.21.1. Cada jogador pode ter uma mina ativa por vez, com blocos de minério configuráveis e sistema de expiração automática totalmente configurável.

## Funcionalidades

### Comando Principal
- `/nyris` - Recebe o item especial da mina

### Sistema de Minas
- **Cubo Perfeito**: Dimensões lado x lado x altura em blocos
- **Criação Direta**: Clique com botão direito para criar a mina onde está olhando
- **Posicionamento de Borda**: A mina é criada a partir do ponto de clique (não centralizada)
- **Spawn de Blocos**: Cria blocos de minério no ar, não substitui blocos existentes
- **Sistema de Expiração**: Tempo totalmente configurável
- **Regeneração Automática**: Mundo é restaurado ao estado anterior automaticamente
- **Efeitos de Criação**: Som de XP, título na tela e partículas configuráveis
- **Minérios Aleatórios**: Usa minérios da lista configurada
- **Proteção**: Apenas o dono pode minerar na mina
- **Regeneração Inteligente**: Restaura apenas os blocos modificados

## Instalação

1. Baixe o arquivo .jar do plugin
2. Coloque-o na pasta `plugins` do seu servidor
3. Reinicie o servidor
4. O plugin será carregado automaticamente

## Configuração

### config.yml
```yaml
mine:
  dimensions:
    side: 10      # Lado do cubo (largura x profundidade)
    height: 5     # Altura do cubo
  
  duration:
    active_time: 3600    # Tempo ativo em segundos (3600 = 1 hora)
    warning_time: 300    # Tempo de aviso em segundos (300 = 5 minutos)
    
    # Exemplos de tempo:
    # 60 = 1 minuto
    # 300 = 5 minutos  
    # 600 = 10 minutos
    # 1800 = 30 minutos
    # 3600 = 1 hora
    # 7200 = 2 horas
    # 86400 = 1 dia
  
  ore_types:
    - DIAMOND_ORE
    - GOLD_ORE
    - EMERALD_ORE
    - LAPIS_ORE
    - REDSTONE_ORE
    - COPPER_ORE
    - DEEPSLATE_COAL_ORE
    - DEEPSLATE_IRON_ORE
    - DEEPSLATE_GOLD_ORE
    - DEEPSLATE_DIAMOND_ORE
    - DEEPSLATE_EMERALD_ORE
    - DEEPSLATE_LAPIS_ORE
    - DEEPSLATE_REDSTONE_ORE
    - DEEPSLATE_COPPER_ORE

creation_effects:
  sound:
    enabled: true
    type: ENTITY_PLAYER_LEVELUP
    volume: 1.0
    pitch: 1.0
  
  title:
    enabled: true
    main_title: "§6§lMINA COLOCADA"
    subtitle: "§fSua mina foi criada com sucesso!"
  
  particles:
    enabled: true
    type: PORTAL
    count: 100
    offset_x: 0.5
    offset_y: 0.5
    offset_z: 0.5

expiration_effects:
  title:
    enabled: true
    main_title: "§c§lMINA EXPIRADA"
    subtitle: "§fSua mina foi removida automaticamente"
```

## Como Usar

1. **Obter o item**: Digite `/nyris` para receber o item especial
2. **Mirar**: Olhe para onde deseja criar a mina
3. **Criar**: Clique com botão direito para criar a mina na localização
4. **Efeitos**: Som de XP, título na tela e partículas especiais
5. **Mineração**: Use sua picareta para minerar os minérios
6. **Avisos**: Receba notificações quando a mina estiver prestes a expirar
7. **Contagem Regressiva**: Avisos de 3, 2, 1 segundo com sons especiais
8. **Expiração**: A mina é removida automaticamente após o tempo configurado
9. **Efeitos Finais**: Som de explosão e título "MINA EXPIRADA"
10. **Regeneração**: O mundo é restaurado automaticamente ao estado anterior

## Sistema de Dimensões

- **Lado**: Número de blocos para largura e profundidade (cubo perfeito)
- **Altura**: Número de blocos para altura
- **Posicionamento de Borda**: A mina é criada a partir do ponto de clique
- **Formato**: Cubo perfeito com dimensões lado x lado x altura

## Sistema de Criação de Blocos

- **Spawn no Ar**: Cria blocos de minério em posições vazias
- **Não Substitui**: Não remove blocos existentes
- **Minérios Aleatórios**: Cada bloco recebe um minério diferente
- **Cubo Completo**: Preenche todo o volume do cubo configurado
- **Posicionamento Inteligente**: Você fica na borda da mina, não no centro

## Sistema de Efeitos de Criação

- **Som de XP**: Som de level up quando a mina é criada
- **Título na Tela**: "MINA COLOCADA" com subtítulo configurável
- **Partículas**: Efeitos visuais especiais na criação
- **Totalmente Configurável**: Todos os efeitos podem ser personalizados
- **Ativação/Desativação**: Cada efeito pode ser ligado ou desligado

### Configuração de Efeitos:

#### Som:
- **Tipo**: ENTITY_PLAYER_LEVELUP (som de XP)
- **Volume**: 0.0 a 1.0
- **Pitch**: Alteração da tonalidade

#### Título:
- **Título Principal**: "MINA COLOCADA" (configurável)
- **Subtítulo**: Mensagem secundária
- **Timing**: Valores fixos otimizados (fade in: 10, stay: 40, fade out: 10)

#### Partículas:
- **Tipo**: PORTAL (efeito de portal)
- **Quantidade**: Número de partículas
- **Offset**: Distribuição espacial das partículas

## Sistema de Efeitos de Expiração

- **Título de Expiração**: "MINA EXPIRADA" quando a mina é removida
- **Subtítulo Configurável**: Mensagem de confirmação personalizável
- **Animações**: Timing fixo otimizado (fade in: 10, stay: 40, fade out: 10)
- **Cores Personalizáveis**: Título em vermelho para destaque
- **Contagem Regressiva**: Avisos de 3, 2, 1 segundo antes da expiração
- **Sons de Contagem**: Som de nota musical durante a contagem regressiva
- **Som Final**: Som de explosão quando a mina é removida
- **Ativação/Desativação**: Pode ser ligado ou desligado

### Configuração de Efeitos de Expiração:

#### Título de Expiração:
- **Título Principal**: "§c§lMINA EXPIRADA" (configurável)
- **Subtítulo**: "§fSua mina foi removida automaticamente" (configurável)
- **Timing**: Valores fixos otimizados (fade in: 10, stay: 40, fade out: 10)

#### Contagem Regressiva:
- **Som de Contagem**: BLOCK_NOTE_BLOCK_PLING (nota musical) - fixo
- **Som Final**: ENTITY_GENERIC_EXPLODE (explosão) - fixo
- **Volume e Pitch**: Valores fixos otimizados

## Sistema de Tempo Configurável

- **Tempo Ativo**: Duração total da mina (totalmente configurável)
- **Tempo de Aviso**: Quando avisar sobre a expiração
- **Flexibilidade**: De segundos a dias
- **Validação**: Sistema verifica configurações inválidas
- **Formatação Inteligente**: Mostra tempo de forma amigável

## Sistema de Persistência Híbrido

- **MySQL Principal**: Sistema de banco de dados robusto para servidores grandes
- **JSON Fallback**: Sistema de arquivo local como backup automático
- **Detecção Inteligente**: Escolhe automaticamente o melhor sistema disponível
- **Conexão Pool**: HikariCP para conexões MySQL otimizadas
- **Fallback Automático**: Se MySQL falhar, usa JSON automaticamente
- **Salvamento Automático**: Todas as minas são salvas automaticamente
- **Restauração Inteligente**: Minas são restauradas após reinicialização do servidor
- **Verificação de Expiração**: Sistema verifica se minas ainda são válidas
- **Agendamento Automático**: Expirações são reagendadas automaticamente
- **Sistema de Pausa**: Tempo é pausado durante reinicialização do servidor
- **Tratamento de Erros**: Sistema robusto com tratamento de exceções
- **Logs Detalhados**: Informações completas sobre salvamento e carregamento

### Configuração do Banco de Dados:

```yaml
database:
  type: "mysql"  # mysql ou json
  mysql:
    host: "localhost"
    port: 3306
    database: "nyris_plugin"
    username: "root"
    password: "sua_senha_aqui"
    connection_pool_size: 10
    connection_timeout: 30000
    idle_timeout: 600000
    max_lifetime: 1800000
  fallback:
    enabled: true
    json_file: "mines.json"
```

### Como Funciona a Persistência Híbrida:

1. **Inicialização**: Plugin tenta conectar ao MySQL
2. **Teste de Conexão**: Verifica se MySQL está funcionando
3. **Escolha do Sistema**: 
   - ✅ **MySQL**: Se conexão bem-sucedida
   - ⚠️ **JSON**: Se MySQL falhar (fallback automático)
4. **Criação da Mina**: Mina é salva no sistema ativo
5. **Modificações**: Qualquer alteração aciona salvamento automático
6. **Reinicialização**: Servidor é reiniciado
7. **Carregamento**: Plugin carrega minas do sistema ativo
8. **Verificação**: Sistema verifica se minas ainda são válidas
9. **Restauração**: Minas válidas são restauradas com agendamento
10. **Funcionamento**: Sistema continua funcionando normalmente

### Vantagens do Sistema Híbrido:

- **🚀 Performance**: MySQL para servidores grandes e ativos
- **🔄 Confiabilidade**: Fallback automático se MySQL falhar
- **💾 Escalabilidade**: Suporta milhares de minas simultâneas
- **⚡ Velocidade**: Conexão pool otimizada com HikariCP
- **🛡️ Segurança**: Transações SQL com rollback automático
- **📊 Monitoramento**: Logs detalhados de todas as operações
- **🎯 Flexibilidade**: Configuração fácil entre MySQL e JSON
- **💡 Inteligência**: Escolha automática do melhor sistema

### Exemplo de Funcionamento:

1. **Mina Criada**: Expira em 1 hora (3600 segundos)
2. **Servidor Reinicia**: Após 30 minutos (1800 segundos)
3. **Tempo Pausado**: Sistema marca o momento da pausa
4. **Servidor Retorna**: Plugin carrega a mina
5. **Tempo Ajustado**: Expiração recalculada para 30 minutos restantes
6. **Funcionamento Normal**: Contagem regressiva e expiração funcionam perfeitamente

### Vantagens do Sistema:

- **🔄 Continuidade**: Minas continuam funcionando após reinicialização
- **💾 Persistência**: Dados nunca são perdidos
- **⏸️ Pausa Inteligente**: Tempo não é perdido durante reinicializações
- **⚡ Automático**: Não requer intervenção manual
- **🛡️ Robusto**: Tratamento de erros e validações
- **📊 Logs**: Monitoramento completo do sistema
- **🎯 Inteligente**: Apenas minas válidas são restauradas
- **⏰ Precisão**: Tempo de expiração sempre correto

### Exemplos de Configuração de Tempo:

| Tempo | Segundos | Uso Recomendado |
|-------|----------|-----------------|
| 1 minuto | 60 | Testes rápidos |
| 5 minutos | 300 | Eventos curtos |
| 10 minutos | 600 | Sessões rápidas |
| 30 minutos | 1800 | Sessões médias |
| 1 hora | 3600 | Sessões normais |
| 2 horas | 7200 | Sessões longas |
| 1 dia | 86400 | Eventos especiais |

## Sistema de Expiração e Regeneração

- **Tempo Ativo**: Duração total da mina configurável
- **Aviso**: Notificação antes da expiração
- **Expiração**: Remoção automática da mina
- **Regeneração Automática**: Mundo restaurado ao estado anterior
- **Preservação**: Apenas blocos modificados são restaurados
- **Efeitos**: Sons e partículas em cada evento

## Sistema de Regeneração Inteligente

- **Estado Original**: Guarda o estado do mundo antes da mina
- **Restauração Seletiva**: Restaura apenas os blocos modificados
- **Preservação**: Mantém estruturas existentes intactas
- **Automático**: Não requer intervenção manual
- **Eficiente**: Sistema otimizado para performance

## Características Especiais

- **Cubo Perfeito**: Sempre forma um quadrado perfeito na base
- **Posicionamento de Borda**: Você fica na borda da mina, não no centro
- **Spawn de Blocos**: Cria minérios no ar, não substitui existentes
- **Minérios Aleatórios**: Usa a lista configurada de minérios
- **Efeitos Visuais**: Som de XP, título e partículas na criação
- **Efeitos de Expiração**: Título "MINA EXPIRADA" na remoção
- **Contagem Regressiva**: Avisos de 3, 2, 1 segundo antes da expiração
- **Sons de Contagem**: Som de nota musical durante a contagem regressiva
- **Som Final**: Som de explosão quando a mina é removida
- **Sistema de Persistência**: Minas são salvas automaticamente e restauradas após reinicialização
- **Tempo Flexível**: Configuração de segundos a dias
- **Regeneração Automática**: Mundo sempre restaurado ao estado original
- **Otimizado**: Sistema eficiente sem lag
- **Flexível**: Dimensões totalmente configuráveis
- **Sem Travas**: Você nunca fica preso no meio da mina

## Permissões

- `nyris.use` - Permite usar o comando `/nyris` (padrão: true)

## Compatibilidade

- **Versão do Minecraft**: 1.21.1
- **API**: Spigot/Paper
- **Java**: 21+

## Desenvolvedor

- **Autor**: Morreu
- **Versão**: 1.0-SNAPSHOT

## Suporte

Para suporte ou reportar bugs, entre em contato através do GitHub.
