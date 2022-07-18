package org.example;

import org.antlr.runtime.RecognitionException;
import org.antlr.v4.semantics.SemanticPipeline;
import org.antlr.v4.tool.Alternative;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.Rule;
import org.antlr.v4.tool.ast.*;

import java.util.Random;
import java.util.stream.Collectors;

public class Main{
	
	private static final Random rng = new Random();
	
	public static void main(String[] args){
		// 1. load & parse the antlr grammar
		// 2. walk the praise tree...
		// 3. at every rule, select a random alternative (or token if exceeding max depth)
		// 4. at every token, generate a matching piece of text
		// for now, assume all languages are space delimited
		var g = antlrGrammar("""
				grammar bad;
				
				root: HI b? x;
				b: BYE?;
				x: A | B B;
				
				HI: 'hi';
				BYE: 'bye';
				A: 'op A';
				B: 'op B';
				WS: [ \n\t] -> channel(HIDDEN);
				""");
		new SemanticPipeline(g).process();
		
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
		return visitRule(root, g);
	}
	
	public static String visitAstNode(Object child, Grammar g){
		return switch(child){
			case TerminalAST terminal -> visitToken();
			case RuleRefAST ruleRef -> {
				String name = ruleRef.toString(); // yeah
				Rule rule = g.getRule(name);
				yield visitRule(rule, g);
			}
			case OptionalBlockAST opt -> visitOptional(opt, g);
			case BlockAST block -> fromAltAst((AltAST)block.getChild(0), g);
			default -> throw new IllegalStateException("Unexpected value: " + child.getClass());
		};
	}
	
	public static String visitOptional(OptionalBlockAST opt, Grammar g){
		if(rng.nextBoolean())
			return "";
		System.out.println(opt.getChildren().stream().map(x -> x.getClass().getName()).collect(Collectors.joining(", ")));
		return visitAstNode(opt.getChild(0), g);
	}
	
	public static String visitRule(Rule rule, Grammar g){
		System.out.println(rule.getClass());
		Alternative[] alts = rule.alt;
		Alternative toPick = alts[1 + rng.nextInt(alts.length - 1)];
		return fromAltAst(toPick.ast, g);
	}
	
	public static String fromAltAst(AltAST toPick, Grammar g){
		return toPick.getChildren().stream().map(child -> visitAstNode(child, g)).collect(Collectors.joining(" "));
	}
	
	public static String visitToken(){
		return "T";
	}
}