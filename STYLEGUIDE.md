# Google Java Style Guide

> Source: https://google.github.io/styleguide/javaguide.html

## 1 Introduction

This document serves as the **complete** definition of Google's coding standards for source code in the Java™ Programming Language.

### 1.1 Terminology notes

- *class* includes normal class, record class, enum class, interface or annotation type (`@interface`).
- *member* includes nested class, field, method, *or constructor*.
- *comment* refers to *implementation* comments (not Javadoc).

## 2 Source file basics

### 2.1 File name
The file name equals the case-sensitive name of the top-level class plus `.java`.

### 2.2 File encoding
Source files are encoded in **UTF-8**.

### 2.3 Special characters
- Only ASCII horizontal space (0x20) is used for whitespace (no tabs).
- Special escape sequences (`\b`, `\t`, `\n`, `\f`, `\r`, `\s`, `\"`, `\'`, `\\`) are used instead of octal/Unicode escapes.

## 3 Source file structure

In order:
1. License or copyright information
2. Package declaration (not line-wrapped)
3. Imports
4. Exactly one top-level class

**Exactly one blank line** separates each section.

### 3.3 Imports
- **No wildcard imports** — static or otherwise.
- **No module imports**.
- **No line-wrapping** of imports.
- Order: static imports first, then non-static imports, separated by one blank line. Within each group, ASCII sort order.
- Static import is not used for static nested classes.

### 3.4 Class declaration
- Each top-level class resides in its own source file.
- Members/initializers follow ***some* logical order**.
- **Overloads: never split** — methods/constructors with the same name appear in a single contiguous group.

## 4 Formatting

### 4.1 Braces

#### 4.1.1 Use of optional braces
Braces are used with `if`, `else`, `for`, `do` and `while` even when the body is empty or single-statement.

#### 4.1.2 Nonempty blocks: K & R style
- No line break before the opening brace.
- Line break after the opening brace.
- Line break before the closing brace.
- Line break after the closing brace *only if* it terminates a statement or the body of a method, constructor, or named class.

#### 4.1.3 Empty blocks
May be concise (`{}`) unless part of a multi-block statement (`if/else`, `try/catch/finally`).

### 4.2 Block indentation: +2 spaces

### 4.3 One statement per line

### 4.4 Column limit: 100 characters
Exceptions: package declarations, imports, text blocks, command lines in comments, very long identifiers.

### 4.5 Line-wrapping

#### 4.5.1 Where to break
- Break at a **higher syntactic level**.
- Break *before* non-assignment operators (`.` , `::`, `|`, `&` in type bounds).
- Break *after* assignment operators (either side is acceptable).
- Method/constructor name stays attached to the opening `(`.
- A comma stays attached to the preceding token.
- Never break adjacent to lambda `->` or switch rule `->`, except immediately after it for a single unbraced expression.

#### 4.5.2 Indent continuation lines at least +4 spaces

### 4.6 Whitespace

#### 4.6.1 Vertical whitespace
A single blank line appears:
- Between consecutive members or initializers of a class.
- Between sections as required by other rules.
Multiple consecutive blank lines are permitted but never required.

#### 4.6.2 Horizontal whitespace
A single ASCII space appears:
- After keywords (`if`, `for`, `catch`) before `(`
- Before `{` (with exceptions)
- On both sides of binary/ternary operators (except `.` and `::`)
- After `,`, `;`, `)`, `//`
- Between type and identifier in declarations

#### 4.6.3 Horizontal alignment: never required

### 4.8 Specific constructs

#### 4.8.1 Enum classes
Optional line break after commas. May be formatted like an array initializer if no methods/docs.

#### 4.8.2 Variable declarations
- **One variable per declaration** (exception: `for` loop header).
- Declared close to first use, typically with an initializer.

#### 4.8.3 Arrays
- C-style array declarations are not used (`String[] args`, not `String args[]`).
- Array initializers may be formatted like block-like constructs.

#### 4.8.4 Switch
- Contents indented +2, labels at +2.
- **Fall-through must be commented** (`// fall through`).
- **Every switch must be exhaustive** (include `default` even if empty).
- Switch expressions must use new-style (`->`).

#### 4.8.5 Annotations
- Class/package/module annotations: one per line.
- Method/constructor annotations: one per line (exception: single parameterless annotation may share the line).
- Field annotations: multiple may appear on the same line.

#### 4.8.6 Comments
- Block comments indented at the same level as surrounding code.
- `TODO` comments: format `// TODO: <resource> - <description>`.

#### 4.8.7 Modifiers
Order: `public protected private abstract default static final sealed non-sealed transient volatile synchronized native strictfp`

#### 4.8.8 Numeric literals
`long` suffix is uppercase `L`.

## 5 Naming

### 5.1 Rules common to all identifiers
ASCII letters and digits only (underscores in rare cases). No special prefixes or suffixes (`name_`, `mName`, `s_name`, `kName`).

### 5.2 Rules by identifier type

| Type | Style | Examples |
|------|-------|----------|
| Package/module | Lowercase only, concatenated | `com.example.deepspace` |
| Class | UpperCamelCase | `Character`, `ImmutableList` |
| Method | lowerCamelCase | `sendMessage`, `stop` |
| Constant | UPPER_SNAKE_CASE | `MAX_VALUE`, `NAMES` |
| Non-constant field | lowerCamelCase | `computedValues`, `index` |
| Parameter | lowerCamelCase | `input`, `label` |
| Local variable | lowerCamelCase | `result`, `i` |
| Type variable | Single capital letter or class-style + `T` | `E`, `T`, `RequestT` |
| Unnamed variable | `_` | `Predicate<String> alwaysTrue = _ -> true;` |

### 5.3 Camel case: defined
1. Convert to plain ASCII, remove apostrophes.
2. Split into words on spaces/punctuation.
3. Lowercase everything, then uppercase first character of each word (UpperCamelCase) or each word except the first (lowerCamelCase).
4. Join words.

## 6 Programming Practices

### 6.1 `@Override`: always used
Whenever a method overrides a supertype method, implements an interface method, or respecifies a superinterface method.

### 6.2 Caught exceptions: not ignored
If truly appropriate to take no action, explain in a comment.

### 6.3 Static members: qualified using class
`Foo.aStaticMethod()`, not `aFoo.aStaticMethod()`.

### 6.4 Finalizers: not used
Do not override `Object.finalize`.

## 7 Javadoc

### 7.1 Formatting
- Basic form: `/** ... */` with aligned `*` on continuation lines.
- Single-line form acceptable when everything (including tags) fits on one line.

### 7.2 The summary fragment
Brief noun phrase or verb phrase (not a complete sentence), capitalized and punctuated as if it were a complete sentence.

### 7.3 Where Javadoc is used
Required for every visible class, member, or record component, except:
- Self-explanatory members (e.g., simple `getFoo()`).
- Methods that override a supertype method.
