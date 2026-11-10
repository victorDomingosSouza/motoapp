# Corrida Inteligente — atualização

Extraia por cima da pasta do projeto (`motoapp/`), substituindo os arquivos.
Não mexe em `gradle.properties`, `local.properties` nem nos arquivos do Gradle.

## Fluxo
1. Aba **Automático** → "Exibir sobre outros apps" → *Iniciar monitoramento* → autorize a captura.
2. Oferta no app de corridas → OCR, confirmação em 2 leituras iguais → faixa com veredito,
   R$/km, R$/min e os dados lidos (valor, km e minutos de busca/viagem).
3. Oferta diferente → a faixa antiga some na hora; a nova entra depois de confirmada.
4. Você aceita/recusa no app de corridas. Se aparecer um texto de "corrida aceita" (aba Critérios),
   a corrida é gravada uma única vez e a faixa some.

## Economia de bateria
- Sem oferta: amostra 1x/s uma miniatura 32x56 lida do buffer; OCR só se ≥10% da miniatura mudou de
  uma vez, ou a cada 6 s como rede de segurança.
- Com oferta/aceite pendente: 2 amostras/s; OCR com mudança ≥1,5% ou a cada 1,5 s.
- A área da própria faixa é apagada do frame antes de tudo (nunca vira "oferta").
- A faixa não recebe toques: não atrapalha o app de corridas.

Banco na versão 2 (R$/hora virou R$/min): o histórico antigo é apagado na primeira execução.
