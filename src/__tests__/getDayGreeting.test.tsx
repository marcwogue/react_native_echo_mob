import { expect, test } from '@jest/globals';
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
