# Corrida Inteligente

App Android (Kotlin + Jetpack Compose) que diz se uma corrida compensa: ACEITAR / ATENÇÃO / RECUSAR.

## Como rodar
1. Android Studio (Ladybug ou mais novo) → *Open* → pasta `CorridaInteligente`.
2. Crie uma chave do Maps SDK for Android no Google Cloud e adicione ao `local.properties`:
   `MAPS_API_KEY=sua_chave`
3. Sync Gradle e execute em um aparelho/emulador (minSdk 26).

## Fluxo automático (principal)
1. Aba **Automático** → conceda "Exibir sobre outros apps" → **Iniciar monitoramento** → autorize a captura de tela.
2. Abra o app de corridas e deixe trabalhando. O serviço (`service/MonitorService.kt`) captura a tela
   a cada ~0,6 s, roda o OCR do ML Kit e interpreta a oferta (`OfferParser`).
3. Leitura estável em 2 frames seguidos → faixa flutuante com ACEITAR / ATENÇÃO / RECUSAR, ganho por km e por hora.
4. **Aceitei** no overlay grava a corrida (Room, com localização). A faixa some sozinha quando a oferta sai da tela.
5. Pare pela notificação ou pela aba Automático. A aba **Manual** (print) continua útil para testar o parser.

## Fluxo manual
- **Analisar**: tire/selecione um print da oferta → ML Kit lê o texto → campos preenchidos (editáveis) →
  veredito instantâneo. "Aceitei" salva no Room (com localização); "Recusei" limpa.
- **Histórico**: nº de corridas, total, média R$/km e R$/h, melhores horários e regiões.
- **Mapa**: células de ~1 km coloridas pelo seu R$/km médio versus seu mínimo.
- **Critérios** (DataStore): mín. R$/km, mín. R$/h, valor mínimo, busca máxima, custo por km, bruto/líquido.

## Regra de decisão
ganho = valor − custo/km × (km busca + km viagem)   (ou bruto, se preferir)
R$/km = ganho / km total · R$/h = ganho / (min busca + min viagem) × 60
- ACEITAR: passa em todos os critérios · ATENÇÃO: falha em 1 critério · RECUSAR: falha em 2+.

## Ajustando o OCR
`domain/OfferParser.kt` assume o padrão "R$ 12,50 … 5 min (1,8 km) … 14 min (6,2 km)".
Cada app tem um layout; ajuste as regex ao seu.
