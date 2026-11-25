# Revisão independente da variante de travas 1721

NÃO INTEGRAR. A revisão foi por leitura, sem compilação ou teste no Forge real. Além das limitações já documentadas, ela apontou:

1. `setAutoTemperatureTarget` altera o alvo e chama `markDirty` sem guarda da própria transação; callback que chame o mesmo setter pode causar recursão e StackOverflowError. Os testes abrangem setters durante parada, não esta reentrada. Ainda não reproduzido no Forge.
2. `extractEnergyChecked` debita energia antes de `markDirty`; se a notificação lançar, o chamador pode não receber o valor extraído apesar do débito. Hipótese por leitura não reproduzida no Forge.
3. Persistem risco de perda de combustível caso a extração modifique o inventário antes de notificação lançar, falta de garantia de persistência do SCRAM após falha de notificação ou crash e ausência de testes do NBT completo e do Minecraft/Forge reais. Não tratar stubs como garantia de segurança.
4. Teste original AutoThermalTest ainda falha em bounds na temperatura inicial 999. Há teste derivado abaixo do limite e testes específicos da trava; não apagar ou ocultar a falha original.
