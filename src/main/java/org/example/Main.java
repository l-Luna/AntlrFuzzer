package org.example;

import org.antlr.runtime.RecognitionException;
import org.antlr.v4.semantics.SemanticPipeline;
import org.antlr.v4.tool.Alternative;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.Rule;
import org.antlr.v4.tool.ast.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Main{
	
	private static final Random rng = new Random();
	public static final int MAX_DEPTH = 25;
	
	private static final String[] CHAR_GROUPS = {
			"abcdefghijklmnopqrstuvwxyz",
			"ABCDEFGHIJKLMNOPQRSTUVWXYZ",
			"0123456789"
	};
	private static final List<String> ALL_CHARS =
			Arrays.stream(CHAR_GROUPS)
					.flatMapToInt(String::chars)
					.mapToObj(x -> String.valueOf((char)x))
					.toList();
	
	public static void main(String[] args){
		
		Path in = Path.of(args[0]).toAbsolutePath().normalize();
		String text;
		try{
			text = Files.readString(in);
		}catch(IOException e){
			throw new RuntimeException(e);
		}
		// 1. load & parse the antlr grammar
		// 2. walk the praise tree...
		// 3. at every rule, select a random alternative (or token if exceeding max depth)
		// 4. at every token, generate a matching piece of text
		// for now, assume all languages are space delimited
		var g = antlrGrammar(text);
		new SemanticPipeline(g).process();
		
		for(int i = 0; i < 10; i++)
			System.out.println(generate(g));
	}
	
	public static Grammar antlrGrammar(String grammar){
		try{
			return new Grammar(grammar);
		}catch(RecognitionException e){
			throw new RuntimeException(e);
		}
	}
	
	public static String generate(Grammar g){
		Rule root = g.getRule(0);
		return visitRule(root, g, 0);
	}
	
	// generation from rules
	
	public static String astNodeRec(Object child, Grammar g, int depth){
		if(depth >= MAX_DEPTH)
			return "NEVERMIND";
		try{
			return switch(child){
				case GrammarAST gr && gr.getClass() == GrammarAST.class -> {
					// we have to rely on name info here
					if(gr.toString().equals("SET"))
						yield astNodeRec(random(gr.getChildrenAsArray()), g, depth + 1);
					if(gr.toString().equals("="))
						yield "";
					throw new IllegalStateException("Unexpected grammar node: " + gr);
				}
				case TerminalAST terminal -> visitToken(terminal.toString(), g.getImplicitLexer());
				case RuleRefAST ruleRef -> {
					String name = ruleRef.toString(); // yeah
					Rule rule = g.getRule(name);
					yield visitRule(rule, g, depth + 1);
				}
				case OptionalBlockAST opt -> visitOptional(opt, g, depth + 1);
				case BlockAST block -> fromAltAst((AltAST)block.getChild(0), g, depth + 1);
				case StarBlockAST star -> visitStar(star, g, depth + 1);
				case PlusBlockAST pls -> visitPlus(pls, g, depth + 1);
				case ActionAST ignored -> "";
				default -> throw new IllegalStateException("Unexpected value: " + child.getClass());
			};
		}catch(StackOverflowError soe){
			return "OOPS";
		}
	}
	
	public static String visitOptional(OptionalBlockAST opt, Grammar g, int depth){
		if(rng.nextBoolean())
			return "";
		return astNodeRec(opt.getChild(0), g, depth);
	}
	
	public static String visitPlus(PlusBlockAST pls, Grammar g, int depth){
		int amnt = rng.nextInt(2) + 1;
		StringBuilder stringBuilder = new StringBuilder();
		for(int i = 0; i < amnt; i++){
			if(i != 0)
				stringBuilder.append(" ");
			stringBuilder.append(astNodeRec(pls.getChild(0), g, depth));
		}
		return stringBuilder.toString();
	}
	
	public static String visitStar(StarBlockAST star, Grammar g, int depth){
		int amnt = rng.nextInt(3);
		StringBuilder stringBuilder = new StringBuilder();
		for(int i = 0; i < amnt; i++){
			if(i != 0)
				stringBuilder.append(" ");
			stringBuilder.append(astNodeRec(star.getChild(0), g, depth));
		}
		return stringBuilder.toString();
	}
	
	public static String visitRule(Rule rule, Grammar g, int depth){
		Alternative[] alts = rule.alt;
		Alternative toPick = alts[1 + rng.nextInt(alts.length - 1)];
		return fromAltAst(toPick.ast, g, depth);
	}
	
	public static String fromAltAst(AltAST toPick, Grammar g, int depth){
		return toPick.getChildren().stream().map(child -> astNodeRec(child, g, depth)).collect(Collectors.joining(" "));
	}
	
	// generation from tokens/terminals
	// TODO: dedup lol
	
	public static String visitToken(String rulename, Grammar g){
		if(rulename.equals("EOF"))
			return "\n";
		Rule rule = g.getRule(rulename);
		if(rule == null)
			throw new NullPointerException("Missing rule " + rulename + " from " + Arrays.toString(g.getRuleNames()));
		Alternative[] alts = rule.alt;
		Alternative toPick = alts[1 + rng.nextInt(alts.length - 1)];
		return toPick.ast.getChildren().stream().map(x -> terminalNodeRec(x, g)).collect(Collectors.joining());
	}
	
	public static String terminalNodeRec(Object child, Grammar g){
		return switch(child){
			case TerminalAST t -> {
				if(t.toString().startsWith("'") && t.toString().endsWith("'")){
					String tt = t.toString();
					yield tt.substring(1, tt.length() - 1);
				}
				yield visitToken(t.toString(), g);
			}
			case OptionalBlockAST opt -> visitOptionalTerminal(opt, g);
			case PlusBlockAST pls -> visitPlusTerminal(pls, g);
			case StarBlockAST star -> visitStarTerminal(star, g);
			case BlockAST block -> block.getChildren().stream()
					.flatMap(x -> ((AltAST)x).getChildren().stream())
					.map(x -> terminalNodeRec(x, g))
					.collect(Collectors.joining());
			case GrammarAST gr && gr.getClass() == GrammarAST.class -> visitOtherGrammarTerminal(gr, g);
			case NotAST ignored -> ""; // its not there
			default -> throw new IllegalStateException("o: " + child.getClass().getName());
		};
	}
	
	public static String visitOptionalTerminal(OptionalBlockAST opt, Grammar g){
		if(rng.nextBoolean())
			return "";
		return terminalNodeRec(opt.getChild(0), g);
	}
	
	public static String visitPlusTerminal(PlusBlockAST pls, Grammar g){
		int amnt = rng.nextInt(5) + 1;
		StringBuilder stringBuilder = new StringBuilder();
		for(int i = 0; i < amnt; i++){
			stringBuilder.append(terminalNodeRec(pls.getChild(0), g));
		}
		return stringBuilder.toString();
	}
	
	public static String visitStarTerminal(StarBlockAST star, Grammar g){
		int amnt = rng.nextInt(6);
		StringBuilder stringBuilder = new StringBuilder();
		for(int i = 0; i < amnt; i++){
			stringBuilder.append(terminalNodeRec(star.getChild(0), g));
		}
		return stringBuilder.toString();
	}
	
	public static String visitOtherGrammarTerminal(GrammarAST gr, Grammar g){
		// always character groups?
		String text = gr.toString();
		if(!(text.startsWith("[") && text.endsWith("]")))
			throw new IllegalStateException();
		text = text.substring(1, text.length() - 1);
		
		boolean invert = text.startsWith("^");
		if(invert)
			text = text.substring(1);
		
		Set<String> allowed = new HashSet<>(text.length());
		Character last = null;
		boolean escaped = false, grouping = false;
		for(char c : text.toCharArray()){
			if(escaped)
				allowed.add(String.valueOf(c));
			else if(c == '\\')
				escaped = true;
			else if(c == '-'){
				if(last == null)
					throw new IllegalArgumentException();
				grouping = true;
			}else{
				if(grouping){
					// last must be nonnull or grouping would have thrown
					for(String group : CHAR_GROUPS){
						int start = group.indexOf(last.toString()), end = group.indexOf(String.valueOf(c));
						if(start != -1 && end != -1)
							allowed.addAll(group.substring(start, end + 1).chars().mapToObj(x -> String.valueOf((char)x)).toList());
					}
				}
				last = c;
				allowed.add(String.valueOf(c));
			}
		}
		
		if(invert){
			var all = new HashSet<String>(ALL_CHARS.size() - allowed.size());
			all.addAll(ALL_CHARS);
			all.removeAll(allowed);
			allowed = all;
		}
		
		return random(allowed.toArray(new String[0]));
	}
	
	private static <T> T random(T[] from){
		return from[rng.nextInt(from.length)];
	}
}