# Corrida Inteligente

Aplicativo Android independente que analisa ofertas de corrida e ajuda o motorista a comparar valor, distância, tempo e critérios pessoais antes de decidir.

> Projeto pessoal de portfólio, desenvolvido para praticar desenvolvimento Android nativo e apresentar soluções com Kotlin, Jetpack Compose, OCR e serviços do Android. Estou em busca de oportunidades de estágio e emprego na área de tecnologia.

## Sobre o projeto

O Corrida Inteligente transforma informações visíveis em uma oferta de corrida em uma estimativa de ganho por quilômetro e por minuto. O resultado aparece como **ACEITAR**, **ATENÇÃO** ou **RECUSAR**, conforme os limites configurados pelo motorista.

O app não se conecta às APIs da Uber ou da 99 e não aceita corridas pelo usuário. No modo automático, ele usa a captura de tela autorizada pelo Android e OCR para ler a oferta; a decisão e o aceite continuam sendo do motorista.

## Funcionalidades implementadas

- **Análise manual:** seleciona uma imagem de oferta, extrai o texto com OCR e permite revisar os campos lidos.
- **Monitoramento automático:** observa a tela durante uma sessão autorizada e espera duas leituras iguais antes de apresentar uma nova oferta.
- **Faixa flutuante:** mostra o veredito, os valores lidos e as métricas sem interceptar os toques do app de corridas.
- **Detecção configurável de aceite:** procura textos definidos pelo usuário na tela após a oferta e registra a corrida uma vez quando encontra um sinal compatível.
- **Critérios pessoais:** configura mínimos de R$/km, R$/min e valor da corrida, distância máxima de busca, custo por quilômetro e cálculo bruto ou líquido.
- **Histórico local:** registra corridas detectadas no monitoramento automático, com data, métricas e localização disponível.
- **Resumo e mapa:** apresenta estatísticas do histórico e regiões aproximadas agrupadas a partir das localizações salvas.
- **Uso mais eficiente do OCR:** compara pequenas amostras da tela e chama o OCR principalmente quando detecta mudanças, com leituras periódicas de segurança.

## Como a análise é calculada

No modo líquido, o custo estimado é descontado do valor da oferta:

```text
ganho = valor da corrida - custo por km × distância total
distância total = busca + viagem
R$/km = ganho ÷ distância total
R$/min = ganho ÷ tempo total
```

No modo bruto, o cálculo usa o valor da corrida sem descontar o custo por quilômetro. O veredito compara os resultados com os critérios definidos no app.

## Tecnologias e arquitetura

| Tecnologia ou componente | Uso no projeto |
|---|---|
| Kotlin e Jetpack Compose | Aplicativo nativo e telas |
| ViewModel e Coroutines | Estado da interface e tarefas assíncronas |
| ML Kit Text Recognition | Leitura de texto em imagens e frames da tela |
| MediaProjection e Foreground Service | Captura de tela durante o monitoramento autorizado |
| Room | Histórico local de corridas |
| DataStore | Critérios e textos de detecção configuráveis |
| Google Maps SDK e localização | Mapa e agrupamento aproximado de regiões |

Principais áreas do código:

- `app/src/main/java/.../ui`: telas, navegação e estado da interface.
- `app/src/main/java/.../domain`: leitura dos campos, avaliação da oferta e estatísticas.
- `app/src/main/java/.../data`: OCR, persistência local, critérios e localização.
- `app/src/main/java/.../service`: captura de tela, monitoramento e faixa flutuante.
- `app/src/main/AndroidManifest.xml`: permissões e componentes Android.

## Requisitos

- Android Studio com Android SDK 35 instalado.
- JDK 17.
- Emulador ou aparelho Android com API 26 ou superior.
- Chave do Maps SDK for Android para usar a tela de mapa.

## Abrir e executar

1. Clone ou baixe o repositório.
2. No Android Studio, abra a pasta que contém `settings.gradle.kts` e `gradlew`. Na estrutura atual, essa pasta é `motoapp/`.
3. Aguarde a sincronização do Gradle.
4. Selecione um emulador ou aparelho Android e pressione **Run**.

O teste manual permite selecionar uma imagem de oferta e conferir a leitura. Para experimentar o monitoramento automático, conceda as permissões solicitadas para captura de tela e exibição da faixa sobre outros apps.

## Configurar a chave do Google Maps

1. No Google Cloud, habilite o Maps SDK for Android e crie uma chave de API.
2. Restrinja a chave ao Maps SDK for Android e ao aplicativo Android, informando o pacote `com.example.corridainteligente` e o SHA-1 do certificado de assinatura usado no build.
3. Depois que o Android Studio criar ou atualizar `local.properties` na pasta do projeto, preserve a linha `sdk.dir` e acrescente:

   ```properties
   MAPS_API_KEY=SUA_CHAVE_RESTRITA
   ```

4. Sincronize o Gradle e execute o app.

`local.properties` é um arquivo local e não deve ser enviado ao GitHub. A chave usada no app compilado pode ser extraída do APK; por isso, as restrições no Google Cloud são importantes. O uso do Maps SDK também depende da configuração de faturamento do Google Cloud. Consulte a [documentação oficial para criar e restringir uma chave](https://developers.google.com/maps/documentation/android-sdk/get-api-key) e as [informações de uso e faturamento](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing).

## Limitações conhecidas

- A leitura depende do layout e do idioma do app de corridas; mudanças na tela podem exigir ajustes no parser.
- A gravação automática depende de um texto de aceite configurado em **Critérios**. Se o OCR não encontrar esse texto, ou encontrar um termo genérico em outro contexto, uma corrida pode não ser registrada ou ser identificada incorretamente. Calibre e teste os sinais antes de depender do histórico.
- A captura de tela só funciona durante a sessão autorizada pelo usuário. A localização também depende da permissão correspondente.
- A migração atual do banco pode apagar o histórico anterior ao atualizar de uma versão antiga. Faça uma cópia dos dados importantes antes de instalar a atualização.
- O mapa mostra regiões aproximadas calculadas a partir do histórico. Cadastro de áreas de conforto e áreas a evitar é uma possibilidade futura, descrita no planejamento do projeto.

## Próximas ideias

- Permitir cadastrar regiões preferidas e regiões a evitar, incluindo por horário.
- Criar exemplos de teste para diferentes formatos de oferta e erros comuns de OCR.
- Tornar a migração do histórico segura entre versões.
- Ampliar a calibração dos sinais de aceite para diferentes apps de corridas.

## Portfólio

Este projeto reúne práticas de interface Android, processamento de texto por OCR, permissões de captura de tela, execução em segundo plano, persistência local e integração com mapas. Foi pensado como uma demonstração de aprendizado e evolução contínua em desenvolvimento mobile.
