# Plugin Nyris - Minas Personalizadas

## Descrição
O Plugin Nyris permite que jogadores criem minas personalizadas no Minecraft 1.21.1. Cada jogador pode ter uma mina ativa por vez, com blocos de minério configuráveis e sistema de expiração.

## Funcionalidades

### Comando Principal
- `/nyris` - Recebe o item especial da mina

### Sistema de Minas
- **Dimensões Personalizáveis**: Configure largura x profundidade x altura em blocos
- **Criação Direta**: Clique com botão direito para criar a mina onde está olhando
- **Posicionamento Preciso**: A mina é criada no primeiro bloco sólido que você está mirando
- **Sistema de Expiração**: Minas têm duração configurável
- **Efeitos Visuais e Sonoros**: Efeitos especiais na criação, aviso e expiração
- **Minérios**: Lista configurável de tipos de minérios
- **Proteção**: Apenas o dono pode minerar na mina
- **Regeneração**: Mina se regenera automaticamente após ser esgotada

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
    width: 10      # Largura da mina em blocos
    depth: 10      # Profundidade da mina em blocos
    height: 5      # Altura da mina em blocos
  
  duration:
    active_time: 3600    # Tempo ativo em segundos (3600 = 1 hora)
    warning_time: 300    # Tempo de aviso em segundos (300 = 5 minutos)
  
  ore_types:
    - COAL_ORE
    - IRON_ORE
    - DIAMOND_ORE
    # ... outros minérios

effects:
  creation:        # Efeitos na criação da mina
    sound: ENTITY_ENDER_DRAGON_GROWL
    particles: PORTAL
    particle_count: 50
  
  expiration:      # Efeitos na expiração da mina
    sound: ENTITY_GENERIC_EXPLODE
    particles: EXPLOSION_LARGE
    particle_count: 30
  
  warning:         # Efeitos no aviso de expiração
    sound: BLOCK_NOTE_BLOCK_PLING
    particles: NOTE
    particle_count: 20
```

## Como Usar

1. **Obter o item**: Digite `/nyris` para receber o item especial
2. **Mirar**: Olhe para onde deseja criar a mina
3. **Criar**: Clique com botão direito para criar a mina na localização
4. **Mineração**: Use sua picareta para minerar os minérios
5. **Avisos**: Receba notificações quando a mina estiver prestes a expirar
6. **Expiração**: A mina é removida automaticamente após o tempo configurado

## Sistema de Dimensões

- **Largura (X)**: Número de blocos no eixo X
- **Profundidade (Z)**: Número de blocos no eixo Z  
- **Altura (Y)**: Número de blocos no eixo Y
- **Centro**: A mina é criada centralizada no ponto de clique

## Sistema de Expiração

- **Tempo Ativo**: Duração total da mina
- **Aviso**: Notificação antes da expiração
- **Expiração**: Remoção automática da mina
- **Efeitos**: Sons e partículas em cada evento

## Efeitos Visuais e Sonoros

- **Criação**: Som de dragão + partículas de portal
- **Aviso**: Som de nota musical + partículas de nota
- **Expiração**: Som de explosão + partículas de explosão
- **Configuráveis**: Volume, pitch e quantidade de partículas

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
