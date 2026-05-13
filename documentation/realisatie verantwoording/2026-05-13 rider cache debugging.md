---
tags:
  - Realisatie-verantwoording
created: "2026-05-13T12:37:00"
---
# 2026-05-13 rider cache debugging

- Tool: [[Claude AI]]
- Gebruiker: #user/martijn 

## Situatie

Mijn Rider gaf code issues / errors op het default project, dus ik wilde weten wat er gaande was. Dus ik gebruikte [[Claude AI]] om het te debuggen

## Gebruikt

> [!NOTE] Prompt 1
>rider, template c# project has problems
>
>i assume that it is a .net version mismatch? how do i check?

Here are the most common ways to check:

**Check your project's target framework:**

Open the `.csproj` file and look for the `<TargetFramework>` property:

```xml
<TargetFramework>net8.0</TargetFramework>
```

**Check installed .NET versions:**

```bash
dotnet --list-sdks
dotnet --list-runtimes
```

**Check what Rider is using:**

1. Go to **File > Settings > Build, Execution, Deployment > .NET**
2. Check the configured SDK version(s)

**Quick fixes:**

- If the `.csproj` targets a version you don't have installed, either:
  - Install that .NET version, or
  - Change the `<TargetFramework>` in the `.csproj` to a version you have
  
- Reload the project in Rider after making changes (File > Reload All from Disk or restart Rider)

**What's the error message you're seeing?** That would help narrow it down further. Common ones include:

- "The .NET SDK can't be located"
- "Unknown target framework" 
- Package restoration failures

Which one are you getting?

> [!NOTE] Prompt 2
>that is not the issue, everything is .NET 10
>
>my troubles include:
>Cannot resolve symbol 'ILogger'
>Cannot resolve symbol 'BackgroundService'
>There is no suitable method for override
>Cannot resolve symbol 'LogLevel'
>Class 'Worker' is never instantiated
>
>I created a .NET 10 worker service, but it is like it doesn't include global packages by default

Sounds like Rider's IntelliSense is out of sync, not an actual project issue. Try these in order:

**1. Invalidate Rider's caches:**

File > Invalidate Caches... > select all options > Invalidate and Restart

**2. Check your `.csproj` has the required packages:**

You should see something like this in your `.csproj`:

```xml
<ItemGroup>
  <PackageReference Include="Microsoft.Extensions.Hosting" Version="10.0.0" />
  <PackageReference Include="Microsoft.Extensions.Hosting.WindowsServices" Version="10.0.0" />
</ItemGroup>
```

If missing, add them.

**3. Ensure using statements are present in `Program.cs` and `Worker.cs`:**

```csharp
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Hosting;
```

**4. Rebuild the project:**

```bash
dotnet clean
dotnet restore
dotnet build
```

Then reload in Rider.

**5. If still broken, nuke and rebuild:**

```bash
rm -rf bin obj .rider
dotnet restore
```

Then close and reopen the project in Rider.

The "Worker is never instantiated" warning is expected if you haven't set it up in the DI container yet - that's just a code inspection thing, not breaking.

Does invalidating caches fix it?

## Resultaat

Het heeft na het 2e bericht mij de oplossing gegeven: `File > Invalidate Caches... > select all options > Invalidate and Restart`

## Verbeterpunten

Ik had al een vermoede dus ik had dit ook kunnen googlen.