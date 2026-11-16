# Segurança

Não publique vulnerabilidades em issues. Use o recurso **Report a vulnerability** do GitHub quando ele estiver habilitado.

Credenciais, tokens e dados financeiros reais não podem aparecer no repositório, nos testes ou nos logs. Os dados de demonstração do perfil `demo` são fictícios e identificados como tal, e os valores de `.env.example` servem apenas para um ambiente descartável na máquina de quem desenvolve.

O módulo `dev-auth-server` é uma ferramenta de desenvolvimento: guarda clientes em memória, gera uma chave de assinatura nova a cada inicialização e não deve ser exposto fora da máquina local. Os testes assinam tokens com uma chave RSA gerada durante a execução; nenhuma chave privada é versionada.
