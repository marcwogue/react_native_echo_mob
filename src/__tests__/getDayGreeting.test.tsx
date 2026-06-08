/**
 * @jest-environment node
 */
import { expect, test, jest } from '@jest/globals';

// Mock the TurboModule so TurboModuleRegistry.getEnforcing doesn't throw
// when running in a Node environment without a native binary.
jest.mock('../NativeEchoMob', () => ({
  __esModule: true,
  default: {
    multiply: (a: number, b: number) => a * b,
    getDayGreeting: (n: number) => {
      const days = [
        'dimanche',
        'lundi',
        'mardi',
        'mercredi',
        'jeudi',
        'vendredi',
        'samedi',
      ];
      const index = (((n % 7) as number) + 7) % 7;
      return `bonjour ${days[index]}`;
    },
  },
}));

import { getDayGreeting } from '../getDayGreeting';

test('returns correct day greeting', () => {
  expect(getDayGreeting(0)).toBe('bonjour dimanche');
  expect(getDayGreeting(1)).toBe('bonjour lundi');
  expect(getDayGreeting(2)).toBe('bonjour mardi');
  expect(getDayGreeting(3)).toBe('bonjour mercredi');
  expect(getDayGreeting(4)).toBe('bonjour jeudi');
  expect(getDayGreeting(5)).toBe('bonjour vendredi');
  expect(getDayGreeting(6)).toBe('bonjour samedi');
  expect(getDayGreeting(7)).toBe('bonjour dimanche');
  expect(getDayGreeting(8)).toBe('bonjour lundi');
  expect(getDayGreeting(-1)).toBe('bonjour samedi');
});
