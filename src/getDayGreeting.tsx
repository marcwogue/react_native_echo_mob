export function getDayGreeting(n: number): string {
  const days = [
    'dimanche',
    'lundi',
    'mardi',
    'mercredi',
    'jeudi',
    'vendredi',
    'samedi',
  ];
  const index = ((Math.floor(n) % 7) + 7) % 7;
  return `bonjour ${days[index]}`;
}
